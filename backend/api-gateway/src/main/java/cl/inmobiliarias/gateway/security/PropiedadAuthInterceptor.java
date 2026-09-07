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
 * - Las rutas de LECTURA (GET/OPTIONS) son publicas: cualquiera puede ver el catalogo.
 * - Las rutas de MODIFICACION requieren un token valido. Segun el metodo:
 *     a) Un token emitido por Azure Entra ID (validado por {@link AzureAutorizacion})
 *        que corresponda a un rol con permiso:
 *           - ADMIN  : puede CREAR (POST), EDITAR (PUT) y ELIMINAR (DELETE).
 *           - CORREDOR: puede CREAR (POST) y ELIMINAR (DELETE), pero NO editar (PUT).
 *     b) Un token propio del gateway (JwtUtil) con rol ADMIN.
 *   Si no hay token valido, o el usuario no tiene el rol que exige el metodo,
 *   se rechaza.
 */
@Component
public class PropiedadAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final AzureAutorizacion azureAutorizacion;

    public PropiedadAuthInterceptor(JwtUtil jwtUtil,
                                    AzureAutorizacion azureAutorizacion) {
        this.jwtUtil = jwtUtil;
        this.azureAutorizacion = azureAutorizacion;
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

        // Modificacion o cualquier otro metodo: requiere token con rol.
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return rechazar(response, HttpStatus.UNAUTHORIZED.value(),
                    "{\"error\":\"Se requiere autenticacion. Token no provisto.\"}");
        }

        String token = header.substring(7);

        AzureAutorizacion.Resultado azure = azureAutorizacion.evaluar(token);
        String rolLocal = rolDeTokenPropio(token);

        String rol = rolLocal != null ? rolLocal : (azure != null ? azure.rol() : null);
        if (rol == null) {
            // 401 si no hay token valido; 403 si el token es valido pero el
            // usuario no tiene rol (PUBLIC).
            boolean valido = azure != null || rolLocal != null;
            return rechazar(response, valido ? HttpStatus.FORBIDDEN.value()
                            : HttpStatus.UNAUTHORIZED.value(),
                    valido
                            ? "{\"error\":\"Acceso denegado: el usuario no tiene permisos para esta operacion.\"}"
                            : "{\"error\":\"Token invalido o expirado.\"}");
        }

        if (AzureAutorizacion.ROL_ADMIN.equalsIgnoreCase(rol)) {
            return true;
        }

        if (AzureAutorizacion.ROL_CORREDOR.equalsIgnoreCase(rol)) {
            // El corredor publica (POST) y elimina (DELETE), pero no edita (PUT).
            if ("POST".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                return true;
            }
            return rechazar(response, HttpStatus.FORBIDDEN.value(),
                    "{\"error\":\"El usuario corredor solo puede publicar y eliminar propiedades, no editarlas.\"}");
        }

        return rechazar(response, HttpStatus.FORBIDDEN.value(),
                "{\"error\":\"Acceso denegado: el usuario no tiene permisos para esta operacion.\"}");
    }

    private String rolDeTokenPropio(String token) {
        try {
            if (jwtUtil.esValido(token)) {
                return jwtUtil.getRol(token);
            }
        } catch (Exception ignored) {
            // continua con Azure
        }
        return null;
    }

    private boolean rechazar(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(body);
        return false;
    }
}