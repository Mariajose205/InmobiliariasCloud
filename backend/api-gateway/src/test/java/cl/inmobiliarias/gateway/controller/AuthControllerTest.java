package cl.inmobiliarias.gateway.controller;

import cl.inmobiliarias.gateway.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerTest {

    private AuthController controller;

    @BeforeEach
    void setUp() {
        String secret = "ClaveDePruebaLarguísimaParaFirmarTokensCorrectamente123456";
        JwtUtil jwtUtil = new JwtUtil(secret, 3600000L);
        controller = new AuthController(jwtUtil);

        ReflectionTestUtils.setField(controller, "adminUsername", "admin");
        ReflectionTestUtils.setField(controller, "adminPassword", "Duoc2026");
    }

    @Test
    void loginConCredencialesValidasDevuelveToken() {
        var response = controller.login(new AuthController.LoginRequest("admin", "Duoc2026"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = (java.util.Map<String, Object>) response.getBody();
        assertThat(body.get("token")).isNotNull();
        assertThat(body.get("rol")).isEqualTo("ADMIN");
    }

    @Test
    void loginConCredencialesInvalidasRechaza() {
        var response = controller.login(new AuthController.LoginRequest("admin", "mal"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void loginSinDatosDevuelveBadRequest() {
        var response = controller.login(null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}
