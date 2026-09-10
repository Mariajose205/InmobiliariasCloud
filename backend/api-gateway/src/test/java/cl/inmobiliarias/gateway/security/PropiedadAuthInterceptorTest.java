package cl.inmobiliarias.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PropiedadAuthInterceptorTest {

    private static final String TENANT = "680da6eb-42e0-4147-bda4-8c06e4819411";
    private static final String CLIENT = "f345fdc4-1687-49d0-b0cc-e0d45eb85731";
    private static final String APP_ID_URI = "api://" + CLIENT;
    private static final String SCOPE = "access_as_user";
    private static final String OID_ADMIN = "11111111-1111-1111-1111-111111111111";
    private static final String OID_CORREDOR = "0a73de94-2722-4221-836c-bd7f3af16980";

    private JwtUtil jwtUtil;
    private PropiedadAuthInterceptor interceptor;
    private AzureTokenValidator azureValidator;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() {
        String secret = "ClaveDePruebaLarguísimaParaFirmarTokensCorrectamente123456";
        jwtUtil = new JwtUtil(secret, 3600000L);

        // RestTemplate sin red: no se usara para tokens propios.
        RestTemplate restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());
        azureValidator = new AzureTokenValidator(
                restTemplate, new ObjectMapper(), TENANT, CLIENT,
                "", APP_ID_URI, SCOPE, "");

        AzureAutorizacion autorizacion = new AzureAutorizacion(
                azureValidator,
                "Administrador@InmobiliariaDuoc.onmicrosoft.com",
                OID_ADMIN,
                "Corredor@InmobiliariaDuoc.onmicrosoft.com",
                OID_CORREDOR);

        interceptor = new PropiedadAuthInterceptor(jwtUtil, autorizacion);
        keyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);
    }

    private void inyectarClave(String kid) {
        Map<String, PublicKey> cache = new HashMap<>();
        cache.put(kid, keyPair.getPublic());
        org.springframework.test.util.ReflectionTestUtils.setField(azureValidator, "keyCache", cache);
        org.springframework.test.util.ReflectionTestUtils.setField(azureValidator, "cacheTimestamp",
                new Date().toInstant());
    }

    private String tokenAzure(String kid, String email, String oid) {
        return tokenAzure(kid, email, oid, "https://login.microsoftonline.com/" + TENANT + "/v2.0");
    }

    private String tokenAzure(String kid, String email, String oid, String issuer) {
        Map<String, Object> claims = new HashMap<>();
        if (email != null) {
            claims.put("email", email);
            claims.put("preferred_username", email);
        }
        if (oid != null) {
            claims.put("oid", oid);
        }
        return Jwts.builder()
                .setHeaderParam("kid", kid)
                .setClaims(claims)
                .setSubject(oid != null ? oid : email)
                .setIssuer(issuer)
                .setAudience(APP_ID_URI)
                .claim("scp", SCOPE)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000L))
                .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
                .compact();
    }

    private MockHttpServletRequest request(String method, String token) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, "/api/propiedades");
        if (token != null) {
            req.addHeader("Authorization", "Bearer " + token);
        }
        return req;
    }

    @Test
    void permiteLoginSinToken() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(req, res, new Object())).isTrue();
    }

    @Test
    void permiteLecturaSinToken() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request("GET", null), res, new Object())).isTrue();
    }

    @Test
    void rechazaModificacionSinToken() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request("POST", null), res, new Object());

        assertThat(allowed).isFalse();
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void rechazaModificacionConTokenInvalido() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request("PUT", "token.invalido"), res, new Object());

        assertThat(allowed).isFalse();
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void rechazaModificacionConTokenDeRolNoAdmin() throws Exception {
        String tokenUsuario = jwtUtil.generarToken("cliente", "USUARIO");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request("DELETE", tokenUsuario), res, new Object());

        assertThat(allowed).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    void permiteModificacionConTokenDeRolAdmin() throws Exception {
        String tokenAdmin = jwtUtil.generarToken("admin", "ADMIN");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request("POST", tokenAdmin), res, new Object());

        assertThat(allowed).isTrue();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void permiteUpdateConTokenDeRolAdmin() throws Exception {
        String tokenAdmin = jwtUtil.generarToken("admin", "ADMIN");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request("PUT", tokenAdmin), res, new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void permiteAdminAzureCrearEditarYEliminar() throws Exception {
        inyectarClave("kidA1");
        String token = tokenAzure("kidA1", "Administrador@InmobiliariaDuoc.onmicrosoft.com", OID_ADMIN);

        assertThat(interceptor.preHandle(request("POST", token), new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(interceptor.preHandle(request("PUT", token), new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(interceptor.preHandle(request("DELETE", token), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void permiteCorredorCrearYEliminarPeroNoEditar() throws Exception {
        inyectarClave("kidC1");
        // Token realista: access token SIN email (solo oid del corredor).
        String token = tokenAzure("kidC1", null, OID_CORREDOR);

        MockHttpServletResponse resPost = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("POST", token), resPost, new Object())).isTrue();

        MockHttpServletResponse resDelete = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("DELETE", token), resDelete, new Object())).isTrue();

        MockHttpServletResponse resPut = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("PUT", token), resPut, new Object())).isFalse();
        assertThat(resPut.getStatus()).isEqualTo(403);
    }

    @Test
    void rechazaUsuarioAzureSinRol() throws Exception {
        inyectarClave("kidP1");
        String token = tokenAzure("kidP1", "cliente@inmobiliariaduoc.onmicrosoft.com", null);
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request("POST", token), res, new Object())).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);
    }

    private MockHttpServletRequest requestAuditoria(String method, String token) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, "/api/auditoria");
        if (token != null) {
            req.addHeader("Authorization", "Bearer " + token);
        }
        return req;
    }

    @Test
    void rechazaAuditoriaSinToken() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(requestAuditoria("GET", null), res, new Object());

        assertThat(allowed).isFalse();
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void rechazaAuditoriaConCorredor() throws Exception {
        String tokenCorredor = jwtUtil.generarToken("corredor", "CORREDOR");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(requestAuditoria("GET", tokenCorredor), res, new Object());

        assertThat(allowed).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    void permiteAuditoriaSoloConAdmin() throws Exception {
        String tokenAdmin = jwtUtil.generarToken("admin@inmobiliaria.cl", "ADMIN");
        MockHttpServletRequest req = requestAuditoria("GET", tokenAdmin);
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(req, res, new Object());

        assertThat(allowed).isTrue();
        assertThat(req.getAttribute(PropiedadAuthInterceptor.ATTR_USER)).isEqualTo("admin@inmobiliaria.cl");
        assertThat(req.getAttribute(PropiedadAuthInterceptor.ATTR_USER_ROL)).isEqualTo("ADMIN");
    }
}