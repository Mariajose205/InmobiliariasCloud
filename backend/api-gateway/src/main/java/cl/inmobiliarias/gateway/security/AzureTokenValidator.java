package cl.inmobiliarias.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Valida ACCESS TOKENS v2.0 emitidos por Azure Entra ID para NUESTRA API.
 *
 * A diferencia de la version anterior (que aceptaba el ID Token), aqui se exige:
 *   1. Firma RS256 valida contra el JWKS del tenant (certificado x5c).
 *   2. Emisor (iss) = https://login.microsoftonline.com/{tenant}/v2.0
 *   3. Audiencia (aud) = App ID URI (api://...) y/o GUID del client-id.
 *   4. Scope (scp) = el scope delegado que expone la API (access_as_user).
 *
 * Con esto, el ID Token (no trae scp y su aud es el client de la SPA) y el
 * access token de Graph (aud = graph.microsoft.com, scp = User.Read) dejan de
 * ser validos para las rutas privadas.
 *
 * Devuelve un {@link AzureUser} con el email del usuario autenticado. La
 * comprobacion de si es administrador la realiza el interceptor/endpoint,
 * comparando el email con el configurado.
 */
@Component
public class AzureTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(AzureTokenValidator.class);

    public record AzureUser(String email, String displayName, String objectId, boolean valid) {
        static AzureUser invalido() {
            return new AzureUser(null, null, null, false);
        }
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Set<String> issuers;
    private final String appIdUri;
    private final String requiredScope;
    private final Set<String> audiences;
    private final String jwksUri;

    private static final long CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000L;
    private volatile Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();
    private volatile Instant cacheTimestamp = Instant.EPOCH;

    public AzureTokenValidator(RestTemplate restTemplate,
                               ObjectMapper objectMapper,
                               @Value("${azure.tenant-id}") String tenantId,
                               @Value("${azure.client-id}") String clientId,
                               @Value("${azure.jwks-uri:}") String jwksUri,
                               @Value("${azure.app-id-uri:}") String appIdUri,
                               @Value("${azure.scope:access_as_user}") String scope,
                               @Value("${azure.audiences:}") String audiences) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        // Los ID Tokens v2.0 usan login.microsoftonline.com/{tenant}/v2.0; los
        // ACCESS Tokens de las APIs usan sts.windows.net/{tenant}/. Aceptamos
        // ambos para el mismo tenant.
        this.issuers = new LinkedHashSet<>(Arrays.asList(
                "https://login.microsoftonline.com/" + tenantId + "/v2.0",
                "https://sts.windows.net/" + tenantId + "/"));
        this.appIdUri = (appIdUri == null || appIdUri.isBlank())
                ? "api://" + clientId
                : appIdUri;
        this.requiredScope = (scope == null || scope.isBlank()) ? "access_as_user" : scope;
        this.audiences = parseAudiences(audiences, this.appIdUri, clientId);
        this.jwksUri = (jwksUri == null || jwksUri.isBlank())
                ? "https://login.microsoftonline.com/%s/discovery/v2.0/keys".formatted(tenantId)
                : jwksUri;
    }

    private Set<String> parseAudiences(String config, String appIdUri, String clientId) {
        Set<String> result = new LinkedHashSet<>();
        if (config != null && !config.isBlank()) {
            Arrays.stream(config.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(result::add);
        }
        // Por defecto aceptamos la App ID URI y el GUID del client-id.
        if (result.isEmpty()) {
            result.add(appIdUri);
            result.add(clientId);
        }
        return result;
    }

    /**
     * Valida un bearer token de Azure Entra ID. Devuelve un {@link AzureUser}
     * con {@code valid=false} si el token no es un access token valido para
     * esta API (incluye ID Tokens, tokens de Graph y tokens propios del gateway).
     */
    public AzureUser validate(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return AzureUser.invalido();
        }

        try {
            Map<String, Object> header = decodeHeader(bearerToken);
            if (header == null) {
                log.warn("[AzureTokenValidator] No se pudo decodificar el header del token");
                return AzureUser.invalido();
            }

            String kid = (String) header.get("kid");
            String alg = (String) header.get("alg");
            if (kid == null || alg == null || !"RS256".equalsIgnoreCase(alg)) {
                log.warn("[AzureTokenValidator] Alg/kid no soportado: alg={}, kid={}", alg, kid);
                return AzureUser.invalido();
            }

            PublicKey publicKey = resolvePublicKey(kid);
            if (publicKey == null) {
                log.warn("[AzureTokenValidator] No se resolvio la clave publica del JWKS para kid={}", kid);
                return AzureUser.invalido();
            }

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(bearerToken)
                    .getBody();

            if (!emisorValido(claims)) {
                log.warn("[AzureTokenValidator] Iss invalido: esperados={}, actual={}",
                        issuers, claims.getIssuer());
                return AzureUser.invalido();
            }
            if (!audienciaValida(claims)) {
                log.warn("[AzureTokenValidator] Aud invalido: aud={}, aceptadas={}",
                        String.valueOf(claims.get("aud")), audiences);
                return AzureUser.invalido();
            }
            if (!scopeValido(claims)) {
                log.warn("[AzureTokenValidator] Scope invalido: scp={}, requerido={}",
                        String.valueOf(claims.get("scp")), requiredScope);
                return AzureUser.invalido();
            }

            String email = firstNonNull(
                    claims.get("email", String.class),
                    claims.get("preferred_username", String.class),
                    claims.get("upn", String.class),
                    claims.get("unique_name", String.class));
            String name = claims.get("name", String.class);
            String oid = claims.get("oid", String.class);

            // El oid esta siempre presente en los access tokens de Entra ID.
            // El email/preferred_username solo si la app tiene configurados los
            // optional claims "email"/"preferred_username" para access tokens.
            if (email == null && oid == null) {
                log.warn("[AzureTokenValidator] Token sin oid ni email. Claims presentes: {}",
                        new java.util.TreeMap<>(claims).keySet());
                return AzureUser.invalido();
            }
            return new AzureUser(email, name, oid, true);
        } catch (Exception e) {
            log.warn("[AzureTokenValidator] Error validando token: {}", e.toString());
            return AzureUser.invalido();
        }
    }

    /**
     * El emisor (iss) debe corresponder al tenant. Se aceptan los dos formatos
     * que usa Entra ID: login.microsoftonline.com/{tenant}/v2.0 (ID tokens y
     * algunos access tokens) y sts.windows.net/{tenant}/ (access tokens v2.0).
     */
    private boolean emisorValido(Claims claims) {
        return claims.getIssuer() != null && issuers.contains(claims.getIssuer());
    }

    /**
     * La audiencia (aud) debe estar en la lista de audiencias aceptadas
     * (App ID URI api://... y/o el GUID del client-id).
     */
    private boolean audienciaValida(Claims claims) {
        Object aud = claims.get("aud");
        for (String candidate : aStringList(aud)) {
            if (audiences.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * El scope (scp) debe contener el scope delegado de nuestra API. Los
     * ID Tokens no traen scp; los tokens de Graph traen "User.Read" con otra
     * audiencia. Se acepta el scope corto (access_as_user) o el completo
     * (api://{client-id}/access_as_user).
     */
    private boolean scopeValido(Claims claims) {
        String scopeCompleto = appIdUri + "/" + requiredScope;
        for (String s : aStringList(claims.get("scp"))) {
            if (requiredScope.equalsIgnoreCase(s) || scopeCompleto.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    private List<String> aStringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof String s) {
            values.addAll(Arrays.asList(s.split(" ")));
        } else if (value instanceof Collection<?> collection) {
            for (Object o : collection) {
                if (o instanceof String s) {
                    values.add(s);
                }
            }
        }
        return values;
    }

    private String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private Map<String, Object> decodeHeader(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String json = new String(Base64.getUrlDecoder().decode(parts[0]));
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    private PublicKey resolvePublicKey(String kid) {
        refreshIfStale();
        PublicKey key = keyCache.get(kid);
        if (key != null) {
            return key;
        }
        // Recarga por si el tenant roto sus claves.
        synchronized (this) {
            key = keyCache.get(kid);
            if (key == null) {
                fetchAndCacheKeys();
                key = keyCache.get(kid);
            }
        }
        return key;
    }

    private void refreshIfStale() {
        if (keyCache.isEmpty()
                || Instant.now().toEpochMilli() - cacheTimestamp.toEpochMilli() > CACHE_TTL_MILLIS) {
            synchronized (this) {
                if (keyCache.isEmpty()
                        || Instant.now().toEpochMilli() - cacheTimestamp.toEpochMilli() > CACHE_TTL_MILLIS) {
                    fetchAndCacheKeys();
                }
            }
        }
    }

    private void fetchAndCacheKeys() {
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(jwksUri, JsonNode.class);
            if (response.getBody() == null) {
                throw new IllegalStateException("JWKS vacio de Azure");
            }
            JsonNode keys = response.getBody().path("keys");
            Map<String, PublicKey> fresh = new HashMap<>();
            for (JsonNode node : keys) {
                String kid = node.path("kid").asText(null);
                JsonNode x5c = node.path("x5c");
                if (kid != null && x5c.isArray() && x5c.size() > 0) {
                    PublicKey publicKey = buildPublicKey(x5c.get(0).asText());
                    if (publicKey != null) {
                        fresh.put(kid, publicKey);
                    }
                }
            }
            keyCache = fresh;
            cacheTimestamp = Instant.now();
        } catch (Exception e) {
            // No actualizamos la cache si falla la descarga; mantenemos la anterior.
        }
    }

    private PublicKey buildPublicKey(String x5cB64) {
        try {
            byte[] der = Base64.getDecoder().decode(x5cB64);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf
                    .generateCertificate(new ByteArrayInputStream(der));
            return cert.getPublicKey();
        } catch (Exception e) {
            return null;
        }
    }
}
