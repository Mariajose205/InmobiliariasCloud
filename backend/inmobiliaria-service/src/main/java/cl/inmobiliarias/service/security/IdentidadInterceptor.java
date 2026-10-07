package cl.inmobiliarias.service.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Resuelve la identidad del usuario y aplica la matriz de permisos.
 *
 * Sustituye al `PropiedadAuthInterceptor` que vivia en el api-gateway de Spring
 * Boot. La diferencia es donde ocurre cada comprobacion:
 *
 *  - La AUTENTICACION (firma del token, iss, aud, exp y scope access_as_user)
 *    la hace API Gateway. Si el token no es valido, la peticion nunca llega
 *    aqui y el navegador recibe 401 de AWS.
 *  - La AUTORIZACION (que puede hacer cada rol) se aplica aqui, porque API
 *    Gateway no conoce los roles del negocio ni puede devolver un cuerpo de
 *    error propio en un 403.
 *
 * Las cabeceras `X-User`, `X-User-Oid` y `X-User-Name` se consideran de
 * confianza porque las escribe API Gateway despues de validar el token y
 * porque pisan cualquier valor que mande el cliente.
 *
 * Reglas, identicas a las del gateway anterior:
 *  - GET del catalogo: publico.
 *  - GET /api/me: cualquier usuario autenticado.
 *  - GET /api/auditoria: solo ADMIN.
 *  - POST y DELETE: ADMIN y CORREDOR.
 *  - PUT: solo ADMIN.
 */
@Component
public class IdentidadInterceptor implements HandlerInterceptor {

    public static final String ATTR_USUARIO = "inmobiliaria.usuario";
    public static final String ATTR_ROL = "inmobiliaria.rol";
    public static final String ATTR_NOMBRE = "inmobiliaria.nombre";
    public static final String ATTR_OID = "inmobiliaria.oid";

    public static final String ROL_ADMIN = "ADMIN";
    public static final String ROL_CORREDOR = "CORREDOR";
    public static final String ROL_PUBLIC = "PUBLIC";

    private static final Logger log = LoggerFactory.getLogger(IdentidadInterceptor.class);

    private final SeguridadProperties propiedades;

    public IdentidadInterceptor(SeguridadProperties propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        String uri = request.getRequestURI();
        String metodo = request.getMethod();

        String usuario = limpiar(request.getHeader("X-User"));
        String oid = limpiar(request.getHeader("X-User-Oid"));
        String nombre = limpiar(request.getHeader("X-User-Name"));
        boolean hayIdentidad = !usuario.isBlank() || !oid.isBlank();

        // Catalogo publico: Home.jsx lo pide sin sesion iniciada.
        boolean esAuditoria = uri.startsWith("/api/auditoria");
        boolean esPerfil = uri.startsWith("/api/me");
        if (!esAuditoria && !esPerfil && "GET".equalsIgnoreCase(metodo)) {
            guardar(request, usuario, nombre, oid, hayIdentidad ? resolverRol(usuario, oid) : "");
            return true;
        }

        if (!hayIdentidad) {
            if (propiedades.isAutorizacionRequerida()) {
                // En produccion toda peticion autorizada pasa por API Gateway, que
                // siempre inyecta la identidad. Que falte significa que alguien
                // alcanzo el microservicio por otra via.
                log.warn("[Auth] Peticion sin identidad en {} {}: no vino por el API Gateway", metodo, uri);
                return rechazar(response, 403,
                        "{\"error\":\"Acceso denegado: la peticion no fue validada por el API Gateway.\"}");
            }
            // Desarrollo local sin API Gateway delante.
            guardar(request, "", "", "", "");
            return !"OPTIONS".equalsIgnoreCase(metodo);
        }

        String rol = resolverRol(usuario, oid);
        guardar(request, usuario, nombre, oid, rol);
        log.info("[Auth] {} {} usuario={} rol={}", metodo, uri, usuario, rol);

        if (esPerfil) {
            return true;
        }

        if (esAuditoria) {
            if (ROL_ADMIN.equals(rol)) {
                return true;
            }
            return rechazar(response, 403,
                    "{\"error\":\"Solo el administrador puede ver la auditoria.\"}");
        }

        if (ROL_ADMIN.equals(rol)) {
            return true;
        }

        if (ROL_CORREDOR.equals(rol)) {
            if ("POST".equalsIgnoreCase(metodo) || "DELETE".equalsIgnoreCase(metodo)) {
                return true;
            }
            return rechazar(response, 403,
                    "{\"error\":\"El usuario corredor solo puede publicar y eliminar propiedades, no editarlas.\"}");
        }

        return rechazar(response, 403,
                "{\"error\":\"Acceso denegado: el usuario no tiene permisos para esta operacion.\"}");
    }

    /**
     * Resuelve el rol comparando la identidad reenviada por API Gateway con la
     * configurada. Si el token no trae `preferred_username` la comparacion cae
     * al `oid`, que Azure Entra ID siempre incluye en los access tokens.
     */
    private String resolverRol(String usuario, String oid) {
        if (coincide(propiedades.getAdminEmail(), usuario) || coincide(propiedades.getAdminOid(), oid)) {
            return ROL_ADMIN;
        }
        if (coincide(propiedades.getCorredorEmail(), usuario) || coincide(propiedades.getCorredorOid(), oid)) {
            return ROL_CORREDOR;
        }
        return ROL_PUBLIC;
    }

    private boolean coincide(String configurado, String identidad) {
        return configurado != null && !configurado.isBlank()
                && identidad != null && !identidad.isBlank()
                && configurado.trim().equalsIgnoreCase(identidad.trim());
    }

    private void guardar(HttpServletRequest request, String usuario, String nombre, String oid, String rol) {
        request.setAttribute(ATTR_USUARIO, usuario.isBlank() ? "desconocido" : usuario);
        request.setAttribute(ATTR_ROL, rol == null ? "" : rol);
        request.setAttribute(ATTR_NOMBRE, nombre);
        request.setAttribute(ATTR_OID, oid);
    }

    private String limpiar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private boolean rechazar(HttpServletResponse response, int status, String body) throws IOException {
        log.info("[Auth] RECHAZADO {} {}", status, body);
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(body);
        return false;
    }
}
