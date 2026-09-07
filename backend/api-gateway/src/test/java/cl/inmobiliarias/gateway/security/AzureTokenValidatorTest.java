package cl.inmobiliarias.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AzureTokenValidatorTest {

    private static final String TENANT = "680da6eb-42e0-4147-bda4-8c06e4819411";
    private static final String CLIENT = "f345fdc4-1687-49d0-b0cc-e0d45eb85731";
    private static final String ISSUER = "https://login.microsoftonline.com/" + TENANT + "/v2.0";
    private static final String APP_ID_URI = "api://" + CLIENT;
    private static final String SCOPE = "access_as_user";

    private AzureTokenValidator validator;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());
        validator = new AzureTokenValidator(restTemplate, new ObjectMapper(), TENANT, CLIENT, "",
                "", "access_as_user", "");
        keyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);
    }

    private void inyectarClave(String kid) {
        Map<String, PublicKey> cache = new HashMap<>();
        cache.put(kid, keyPair.getPublic());
        ReflectionTestUtils.setField(validator, "keyCache", cache);
        ReflectionTestUtils.setField(validator, "cacheTimestamp", new Date().toInstant());
    }

    private String firmarToken(String kid, String email, Date exp, String aud, Object scp) {
        return firmarToken(kid, email, exp, aud, scp, ISSUER);
    }

    private String firmarToken(String kid, String email, Date exp, String aud, Object scp, String issuer) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("oid", "aabbccdd-1111-2222-3333-444455556666");
        claims.put("name", "Administrador");
        claims.put("email", email);
        claims.put("preferred_username", email);
        if (scp != null) {
            claims.put("scp", scp);
        }

        return Jwts.builder()
                .setHeaderParam("kid", kid)
                .setClaims(claims)
                .setSubject(email)
                .setIssuer(issuer)
                .setAudience(aud)
                .setIssuedAt(new Date())
                .setExpiration(exp)
                .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
                .compact();
    }

    @Test
    void validaAccessTokenConIssuerStsWindowsNet() {
        inyectarClave("kid7");
        // Los access tokens de Entra ID vienen con iss = https://sts.windows.net/{tenant}/
        String stsIssuer = "https://sts.windows.net/" + TENANT + "/";
        String token = firmarToken("kid7", "administrador@inmobiliariaduoc.onmicrosoft.com",
                new Date(System.currentTimeMillis() + 3600000L), APP_ID_URI, SCOPE, stsIssuer);

        AzureTokenValidator.AzureUser user = validator.validate(token);

        assertThat(user.valid()).isTrue();
        assertThat(user.email()).isEqualTo("administrador@inmobiliariaduoc.onmicrosoft.com");
    }

    @Test
    void validaAccessTokenConAudienciaApiUriYScopeAccessAsUser() {
        inyectarClave("kid1");
        String token = firmarToken("kid1", "administrador@inmobiliariaduoc.onmicrosoft.com",
                new Date(System.currentTimeMillis() + 3600000L), APP_ID_URI, SCOPE);

        AzureTokenValidator.AzureUser user = validator.validate(token);

        assertThat(user.valid()).isTrue();
        assertThat(user.email()).isEqualTo("administrador@inmobiliariaduoc.onmicrosoft.com");
    }

    @Test
    void validaAccessTokenConAudienciaGuidCliente() {
        inyectarClave("kid2");
        String token = firmarToken("kid2", "cliente@inmobiliariaduoc.onmicrosoft.com",
                new Date(System.currentTimeMillis() + 3600000L), CLIENT, SCOPE);

        AzureTokenValidator.AzureUser user = validator.validate(token);

        assertThat(user.valid()).isTrue();
        assertThat(user.email()).isEqualTo("cliente@inmobiliariaduoc.onmicrosoft.com");
    }

    @Test
    void validaAccessTokenSinEmailPeroConOid() {
        inyectarClave("kid8");
        // Los access tokens de una API propia no traen email/preferred_username
        // por defecto; el oid SI esta presente.
        String token = Jwts.builder()
                .setHeaderParam("kid", "kid8")
                .setSubject("aabbccdd-1111-2222-3333-444455556666")
                .setIssuer(ISSUER)
                .setAudience(APP_ID_URI)
                .claim("scp", SCOPE)
                .claim("oid", "aabbccdd-1111-2222-3333-444455556666")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000L))
                .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
                .compact();

        AzureTokenValidator.AzureUser user = validator.validate(token);

        assertThat(user.valid()).isTrue();
        assertThat(user.email()).isNull();
        assertThat(user.objectId()).isEqualTo("aabbccdd-1111-2222-3333-444455556666");
    }

    @Test
    void rechazaIdTokenSinScope() {
        inyectarClave("kid3");
        // El ID token tiene aud = client-id de la SPA y NO trae el claim scp.
        String idToken = firmarToken("kid3", "administrador@inmobiliariaduoc.onmicrosoft.com",
                new Date(System.currentTimeMillis() + 3600000L), CLIENT, null);

        assertThat(validator.validate(idToken).valid()).isFalse();
    }

    @Test
    void rechazaAccessTokenDeGraph() {
        inyectarClave("kid4");
        // Access token de Microsoft Graph: aud = graph.microsoft.com, scp = User.Read.
        String graphToken = firmarToken("kid4", "administrador@inmobiliariaduoc.onmicrosoft.com",
                new Date(System.currentTimeMillis() + 3600000L), "https://graph.microsoft.com", "User.Read");

        assertThat(validator.validate(graphToken).valid()).isFalse();
    }

    @Test
    void rechazaTokenExpirado() {
        inyectarClave("kid5");
        String token = firmarToken("kid5", "administrador@inmobiliariaduoc.onmicrosoft.com",
                new Date(System.currentTimeMillis() - 3600000L), APP_ID_URI, SCOPE);

        assertThat(validator.validate(token).valid()).isFalse();
    }

    @Test
    void rechazaTokenConFirmaNoValida() {
        inyectarClave("kid6");
        // Token firmado con una firma que no corresponde a la clave inyectada.
        KeyPair otroPar = Keys.keyPairFor(SignatureAlgorithm.RS256);
        String token = Jwts.builder()
                .setHeaderParam("kid", "kid6")
                .setSubject("x")
                .setIssuer(ISSUER)
                .setAudience(APP_ID_URI)
                .claim("scp", SCOPE)
                .setExpiration(new Date(System.currentTimeMillis() + 3600000L))
                .signWith(otroPar.getPrivate(), SignatureAlgorithm.RS256)
                .compact();

        assertThat(validator.validate(token).valid()).isFalse();
    }

    @Test
    void rechazaTokenQueNoEsAzure() {
        assertThat(validator.validate("token.invalido")).satisfies(u -> assertThat(u.valid()).isFalse());
        assertThat(validator.validate(null)).satisfies(u -> assertThat(u.valid()).isFalse());
    }
}