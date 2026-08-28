package cl.inmobiliarias.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        String secret = "ClaveDePruebaLarguísimaParaFirmarTokensCorrectamente123456";
        jwtUtil = new JwtUtil(secret, 3600000L);
    }

    @Test
    void generaTokenValidoConRolAdmin() {
        String token = jwtUtil.generarToken("admin", "ADMIN");

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.esValido(token)).isTrue();
        assertThat(jwtUtil.getRol(token)).isEqualTo("ADMIN");
        assertThat(jwtUtil.getSubject(token)).isEqualTo("admin");
    }

    @Test
    void esValidoDevuelveFalseParaTokenCorrupto() {
        assertThat(jwtUtil.esValido("token.falso.corrupto")).isFalse();
    }

    @Test
    void esValidoDevuelveFalseParaTokenVacío() {
        assertThat(jwtUtil.esValido("")).isFalse();
        assertThat(jwtUtil.esValido(null)).isFalse();
    }

    @Test
    void generaTokenConRolConservado() {
        String token = jwtUtil.generarToken("usuario", "USUARIO");

        assertThat(jwtUtil.getRol(token)).isEqualTo("USUARIO");
    }
}
