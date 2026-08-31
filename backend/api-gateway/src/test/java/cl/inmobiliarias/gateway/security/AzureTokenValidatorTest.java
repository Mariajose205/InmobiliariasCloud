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

    private AzureTokenValidator validator;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());
        validator = new AzureTokenValidator(restTemplate, new ObjectMapper(), TENANT, CLIENT, "");
        keyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);
    }

    private void inyectarClave(String kid) {
        Map<String, PublicKey> cache = new HashMap<>();
        cache.put(kid, keyPair.getPublic());
        ReflectionTestUtils.setField(validator, "keyCache", cache);
        ReflectionTestUtils.setField(validator, "cacheTimestamp", new Date().toInstant());
    }

    private String firmarToken(String kid, String email, Date exp) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("oid", "aabbccdd-1111-2222-3333-444455556666");
        claims.put("name", "Administrador");
        claims.put("email", email);
        claims.put("preferred_username", email);

        return Jwts.builder()
                .setHeaderParam("kid", kid)
                .setClaims(claims)
                .setSubject(email)
                .setIssuer(ISSUER)
                .setAudience(CLIENT)
                .setIssuedAt(new Date())
                .setExpiration(exp)
                .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
                .compact();
    }

    @Test
    void validaTokenAzureConEmailDeAdmin() {
        inyectarClave("kid1");
        String token = firmarToken("kid1", "administrador@inmobiliariaduoc.onmicrosoft.com", new Date(System.currentTimeMillis() + 3600000L));

        AzureTokenValidator.AzureUser user = validator.validate(token);

        assertThat(user.valid()).isTrue();
        assertThat(user.email()).isEqualTo("administrador@inmobiliariaduoc.onmicrosoft.com");
    }

    @Test
    void validaTokenAzureDeCliente() {
        inyectarClave("kid2");
        String token = firmarToken("kid2", "cliente@inmobiliariaduoc.onmicrosoft.com", new Date(System.currentTimeMillis() + 3600000L));

        AzureTokenValidator.AzureUser user = validator.validate(token);

        assertThat(user.valid()).isTrue();
        assertThat(user.email()).isEqualTo("cliente@inmobiliariaduoc.onmicrosoft.com");
    }

    @Test
    void rechazaTokenExpirado() {
        inyectarClave("kid3");
        String token = firmarToken("kid3", "administrador@inmobiliariaduoc.onmicrosoft.com", new Date(System.currentTimeMillis() - 3600000L));

        assertThat(validator.validate(token).valid()).isFalse();
    }

    @Test
    void rechazaTokenConFirmaNoAzul() {
        inyectarClave("kid4");
        // Token firmado con una firma que no corresponde a la clave inyectada.
        KeyPair otroPar = Keys.keyPairFor(SignatureAlgorithm.RS256);
        String token = Jwts.builder()
                .setHeaderParam("kid", "kid4")
                .setSubject("x")
                .setIssuer(ISSUER)
                .setAudience(CLIENT)
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
