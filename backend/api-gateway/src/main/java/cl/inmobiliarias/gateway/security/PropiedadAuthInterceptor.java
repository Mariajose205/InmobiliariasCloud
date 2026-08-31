package cl.inmobiliarias.gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Interceptor del API Gateway.
 *
 * - Las rutas de LECTURA (GET) son publicas: cualquiera puede ver el catalogo.
 * - Las rutas de MODIFICACION (POST/PUT/DELETE) requieren un token JWT valido
 *   que corresponda al administrador. Se aceptan dos fuentes de identidad:
 *     a) Un token emitido por Azure Entra ID (validado por {@link AzureTokenValidator}):
 *        es administrador si el email del token coincide con el email admin configurado.
 *     b) Un token propio del gateway (JwtUtil): es administrador si el rol es ADMIN.
 *   Si no hay token valido, o el usuario no es admin, se rechaza.
 */
@Component
public class PropiedadAuthInterceptor implements HandlerInterceptor {

    public static final String ROL_ADMIN = "ADMIN";

    private final JwtUtil jwtUtil;
    private final AzureTokenValidator azureTokenValidator;
    private final String azureAdminEmail;

    public PropiedadAuthInterceptor(JwtUtil jwtUtil,
                                    AzureTokenValidator azureTokenValidator,
                                    @Value("${azure.admin-email}") String azureAdminEmail) {
        this.jwtUtil = jwtUtil;
        this.azureTokenValidator = azureTokenValidator;
        this.azureAdminEmail = azureAdminEmail;
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

        // Modificacion o cualquier otro metodo: requiere token de administrador.
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return rechazar(response, HttpStatus.UNAUTHORIZED.value(),
                    "{\"error\":\"Se requiere autenticacion. Token no provisto.\"}");
        }

        String token = header.substring(7);

        // 1) Token propio del gateway (JWT local HS256).
        if (esAdminPorTokenPropio(token)) {
            return true;
        }

        // 2) Token de Azure Entra ID.
        AzureTokenValidator.AzureUser azureUser = azureTokenValidator.validate(token);
        if (azureUser.valid() && azureAdminEmail != null
                && azureAdminEmail.equalsIgnoreCase(azureUser.email())) {
            return true;
        }

        // Enviar 401 si el token es invalido, 403 si es valido pero no es admin.
        boolean hayTokenValido = azureUser.valid() || esTokenPropioValido(token);
        return rechazar(response, hayTokenValido ? HttpStatus.FORBIDDEN.value()
                        : HttpStatus.UNAUTHORIZED.value(),
                hayTokenValido
                        ? "{\"error\":\"Acceso denegado: solo un administrador puede modificar propiedades.\"}"
                        : "{\"error\":\"Token invalido o expirado.\"}");
    }

    private boolean esAdminPorTokenPropio(String token) {
        try {
            if (jwtUtil.esValido(token)) {
                return ROL_ADMIN.equalsIgnoreCase(jwtUtil.getRol(token));
            }
        } catch (Exception ignored) {
            // continua con Azure
        }
        return false;
    }

    private boolean esTokenPropioValido(String token) {
        try {
            return jwtUtil.esValido(token);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean rechazar(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(body);
        return false;
    }
}
