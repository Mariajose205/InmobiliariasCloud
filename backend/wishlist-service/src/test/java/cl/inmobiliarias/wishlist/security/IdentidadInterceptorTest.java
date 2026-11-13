package cl.inmobiliarias.wishlist.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Matriz de permisos del microservicio (T05 de la rubrica: el rol viaja en
 * claims y el backend lo exige por endpoint).
 *
 * Los dos proveedores de identidad llegan por cabeceras porque API Gateway ya
 * valido el token:
 *  - Entra ID (staff): X-User / X-User-Oid / X-User-Name.
 *  - Cognito (clientes): ademas X-User-Rol con el claim `rol`.
 */
class IdentidadInterceptorTest {

    private SeguridadProperties propiedades;
    private IdentidadInterceptor interceptor;

    @BeforeEach
    void setUp() {
        propiedades = new SeguridadProperties();
        propiedades.setAutorizacionRequerida(true);
        propiedades.setAdminEmail("administrador@inmobiliariaduoc.onmicrosoft.com");
        propiedades.setCorredorEmail("Corredor@InmobiliariaDuoc.onmicrosoft.com");
        interceptor = new IdentidadInterceptor(propiedades);
    }

    private MockHttpServletRequest peticion(String metodo, String uri) {
        return new MockHttpServletRequest(metodo, uri);
    }

    @Test
    void tokenCognitoClientePuedeUsarRutasDeCliente() throws Exception {
        MockHttpServletRequest req = peticion("GET", "/api/cliente/perfil");
        req.addHeader("X-User", "cliente@correo.cl");
        req.addHeader("X-User-Oid", "sub-cognito");
        req.addHeader("X-User-Rol", "CLIENTE");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(req, res, new Object())).isTrue();
        assertThat(req.getAttribute(IdentidadInterceptor.ATTR_ROL)).isEqualTo("CLIENTE");
    }

    @Test
    void tokenCognitoClienteNoPuedeLlamarFuncionesDeAdmin() throws Exception {
        MockHttpServletRequest req = peticion("POST", "/api/admin/kafka/topicos");
        req.addHeader("X-User", "cliente@correo.cl");
        req.addHeader("X-User-Oid", "sub-cognito");
        req.addHeader("X-User-Rol", "CLIENTE");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(req, res, new Object())).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("administrador");
    }

    @Test
    void tokenDeStaffNoPuedeUsarRutasDeCliente() throws Exception {
        MockHttpServletRequest req = peticion("GET", "/api/cliente/favoritos");
        req.addHeader("X-User", "administrador@inmobiliariaduoc.onmicrosoft.com");
        req.addHeader("X-User-Rol", "ADMIN");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(req, res, new Object())).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    void adminDeEntraSigueEntrandoAlPanelSinClaimDeRol() throws Exception {
        MockHttpServletRequest req = peticion("GET", "/api/admin/kafka/topicos");
        req.addHeader("X-User", "Administrador@InmobiliariaDuoc.onmicrosoft.com");
        req.addHeader("X-User-Oid", "oid-admin");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(req, res, new Object())).isTrue();
        assertThat(req.getAttribute(IdentidadInterceptor.ATTR_ROL)).isEqualTo("ADMIN");
    }

    @Test
    void elClaimOperadorSeNormalizaAlRolCorredor() throws Exception {
        MockHttpServletRequest req = peticion("GET", "/api/wishlist/novedades");
        req.addHeader("X-User", "operador@correo.cl");
        req.addHeader("X-User-Rol", "Operador");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(req, res, new Object())).isTrue();
        assertThat(req.getAttribute(IdentidadInterceptor.ATTR_ROL)).isEqualTo("CORREDOR");
    }

    @Test
    void unRolDesconocidoNoConcedePermisosYSeResuelvePorEmail() throws Exception {
        MockHttpServletRequest req = peticion("GET", "/api/admin/kafka/topicos");
        req.addHeader("X-User", "administrador@inmobiliariaduoc.onmicrosoft.com");
        req.addHeader("X-User-Rol", "SUPERUSUARIO");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(req, res, new Object())).isTrue();
        assertThat(req.getAttribute(IdentidadInterceptor.ATTR_ROL)).isEqualTo("ADMIN");
    }

    @Test
    void sinIdentidadEnProduccionSeRechaza() throws Exception {
        MockHttpServletRequest req = peticion("GET", "/api/cliente/perfil");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(req, res, new Object())).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    void elCatalogoDeComunasSigueSiendoPublico() throws Exception {
        MockHttpServletRequest req = peticion("GET", "/api/wishlist/comunas");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(req, res, new Object())).isTrue();
    }
}
