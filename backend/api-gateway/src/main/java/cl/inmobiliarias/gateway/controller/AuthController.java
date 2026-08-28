package cl.inmobiliarias.gateway.controller;

import cl.inmobiliarias.gateway.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String ROL_ADMIN = "ADMIN";

    private final JwtUtil jwtUtil;

    @Value("${security.admin.username}")
    private String adminUsername;

    @Value("${security.admin.password}")
    private String adminPassword;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Debe enviar username y password"));
        }

        boolean credencialesValidas =
                adminUsername.equals(request.username()) && adminPassword.equals(request.password());

        if (!credencialesValidas) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales invalidas."));
        }

        String token = jwtUtil.generarToken(request.username(), ROL_ADMIN);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("username", request.username());
        body.put("rol", ROL_ADMIN);
        body.put("tokenType", "Bearer");
        return ResponseEntity.ok(body);
    }

    public record LoginRequest(String username, String password) {
    }
}
