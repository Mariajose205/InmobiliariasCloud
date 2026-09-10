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
 * - La ruta /api/auditoria es de SOLO ADMIN: exige un token con rol ADMIN.
 * - Las rutas de MODIFICACION requieren un token valido. Segun el metodo:
 *     a) Un token emitido por Azure Entra ID (validado por {@link AzureAutorizacion})
 *        que corresponda a un rol con permiso:
 *           - ADMIN  : puede CREAR (POST), EDITAR (PUT) y ELIMINAR (DELETE).
 *           - CORREDOR: puede CREAR (POST) y ELIMINAR (DELETE), pero NO editar (PUT).
 *     b) Un token propio del gateway (JwtUtil) con rol ADMIN.
 *   Si no hay token valido, o el usuario no tiene el rol que exige el metodo,
 *   se rechaza.
 *
 * Ademas, cuando el usuario pasa la autorizacion, guarda el email/rol en los
 * atributos de la peticion (x-user, x-user-rol) para que el proxy los reenvie
 * al microservicio y este registre en la auditoria quien realizo la accion.
 */
@Component
public class PropiedadAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER = "x-user";
    public static final String ATTR_USER_ROL = "x-user-rol";

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

        // Preflight CORS: siempre permitido.
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        boolean esAuditoria = request.getRequestURI().startsWith("/api/auditoria");

        // Lectura publica del catalogo: no requiere auth.
        if (!esAuditoria && "GET".equalsIgnoreCase(method)) {
            return true;
        }

        // Auditoria y modificaciones: requieren token con rol.
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

        // La auditoria es exclusiva del ADMIN.
        if (esAuditoria) {
            if (AzureAutorizacion.ROL_ADMIN.equalsIgnoreCase(rol)) {
                guardarUsuario(request, azure, rolLocal, token, rol);
                return true;
            }
            return rechazar(response, HttpStatus.FORBIDDEN.value(),
                    "{\"error\":\"Solo el administrador puede ver la auditoria.\"}");
        }

        if (AzureAutorizacion.ROL_ADMIN.equalsIgnoreCase(rol)) {
            guardarUsuario(request, azure, rolLocal, token, rol);
            return true;
        }

        if (AzureAutorizacion.ROL_CORREDOR.equalsIgnoreCase(rol)) {
            // El corredor publica (POST) y elimina (DELETE), pero no edita (PUT).
            if ("POST".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                guardarUsuario(request, azure, rolLocal, token, rol);
                return true;
            }
            return rechazar(response, HttpStatus.FORBIDDEN.value(),
                    "{\"error\":\"El usuario corredor solo puede publicar y eliminar propiedades, no editarlas.\"}");
        }

        return rechazar(response, HttpStatus.FORBIDDEN.value(),
                "{\"error\":\"Acceso denegado: el usuario no tiene permisos para esta operacion.\"}");
    }

    /**
     * Guarda la identidad del usuario autenticado en los atributos de la
     * peticion para que el proxy la reenvie al microservicio (auditoria).
     */
    private void guardarUsuario(HttpServletRequest request,
                                AzureAutorizacion.Resultado azure,
                                String rolLocal,
                                String token,
                                String rol) {
        String usuario = null;
        if (azure != null) {
            usuario = azure.usuario().email();
            if (usuario == null || usuario.isBlank()) {
                usuario = azure.usuario().objectId();
            }
        }
        if (rolLocal != null) {
            usuario = jwtUtil.getSubject(token);
        }
        request.setAttribute(ATTR_USER, usuario != null ? usuario : "desconocido");
        request.setAttribute(ATTR_USER_ROL, rol != null ? rol : "");
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