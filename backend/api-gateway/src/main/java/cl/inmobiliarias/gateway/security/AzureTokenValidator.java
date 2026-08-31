package cl.inmobiliarias.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Valida tokens JWT emitidos por Azure Entra ID.
 *
 * El flujo de trabajo:
 * 1. Obtiene la clave publica firman las claves del tenant (JWKS) usando el
 *    certificado x5c publicado por Microsoft.
 * 2. Verifica la firma RS256 del token.
 * 3. Comprueba emisor (iss), audiencia (aud) y expiracion (exp).
 *
 * Devuelve un {@link AzureUser} con el email del usuario autenticado. La
 * comprobacion de si es administrador la realiza el interceptor, comparando
 * el email con el configurado.
 */
@Component
public class AzureTokenValidator {

    public record AzureUser(String email, String displayName, String objectId, boolean valid) {
        static AzureUser invalido() {
            return new AzureUser(null, null, null, false);
        }
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String tenantId;
    private final String clientId;
    private final String jwksUri;

    private static final long CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000L;
    private volatile Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();
    private volatile Instant cacheTimestamp = Instant.EPOCH;

    public AzureTokenValidator(RestTemplate restTemplate,
                               ObjectMapper objectMapper,
                               @Value("${azure.tenant-id}") String tenantId,
                               @Value("${azure.client-id}") String clientId,
                               @Value("${azure.jwks-uri:}") String jwksUri) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.jwksUri = (jwksUri == null || jwksUri.isBlank())
                ? "https://login.microsoftonline.com/%s/discovery/v2.0/keys".formatted(tenantId)
                : jwksUri;
    }

    /**
     * Valida un bearer token de Azure Entra ID. Devuelve un {@link AzureUser}
     * con {@code valid=false} si el token no es valido (incluye los casos en los
     * que no es un token de Azure, p.ej. tokens propios del gateway).
     */
    public AzureUser validate(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return AzureUser.invalido();
        }

        try {
            Map<String, Object> header = decodeHeader(bearerToken);
            if (header == null) {
                return AzureUser.invalido();
            }

            String kid = (String) header.get("kid");
            String alg = (String) header.get("alg");
            if (kid == null || alg == null || !"RS256".equalsIgnoreCase(alg)) {
                return AzureUser.invalido();
            }

            PublicKey publicKey = resolvePublicKey(kid);
            if (publicKey == null) {
                return AzureUser.invalido();
            }

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .requireIssuer("https://login.microsoftonline.com/" + tenantId + "/v2.0")
                    .requireAudience(clientId)
                    .build()
                    .parseClaimsJws(bearerToken)
                    .getBody();

            String email = firstNonNull(claims.get("email", String.class),
                    claims.get("preferred_username", String.class));
            String name = claims.get("name", String.class);
            String oid = claims.get("oid", String.class);

            if (email == null) {
                return AzureUser.invalido();
            }
            return new AzureUser(email, name, oid, true);
        } catch (Exception e) {
            return AzureUser.invalido();
        }
    }

    private String firstNonNull(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
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
