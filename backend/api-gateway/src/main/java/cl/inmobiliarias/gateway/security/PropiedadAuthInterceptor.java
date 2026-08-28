package cl.inmobiliarias.gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Interceptor del API Gateway.
 *
 * - Las rutas de LECTURA (GET) son publicas: cualquiera puede ver el catalogo.
 * - Las rutas de MODIFICACION (POST/PUT/DELETE) requieren un token JWT valido
 *   cuyo rol sea ADMIN. Si no hay token, o el rol no es ADMIN, se rechaza.
 */
@Component
public class PropiedadAuthInterceptor implements HandlerInterceptor {

    public static final String ROL_ADMIN = "ADMIN";

    private final JwtUtil jwtUtil;

    public PropiedadAuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        // El endpoint de login no requiere token.
        if (request.getRequestURI().equals("/api/auth/login")) {
            return true;
        }

        String method = request.getMethod();

        // Lectura: publica, no requiere auth.
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // Modificacion o cualquier otro metodo: requiere token JWT de ADMIN.
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Se requiere autenticacion. Token no provisto.\"}");
            return false;
        }

        String token = header.substring(7);
        if (!jwtUtil.esValido(token)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Token invalido o expirado.\"}");
            return false;
        }

        String rol = jwtUtil.getRol(token);
        if (!ROL_ADMIN.equalsIgnoreCase(rol)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Acceso denegado: solo un administrador puede modificar propiedades.\"}");
            return false;
        }

        return true;
    }
}
