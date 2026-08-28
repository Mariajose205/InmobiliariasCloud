package cl.inmobiliarias.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class PropiedadAuthInterceptorTest {

    private JwtUtil jwtUtil;
    private PropiedadAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        String secret = "ClaveDePruebaLarguísimaParaFirmarTokensCorrectamente123456";
        jwtUtil = new JwtUtil(secret, 3600000L);
        interceptor = new PropiedadAuthInterceptor(jwtUtil);
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
}
