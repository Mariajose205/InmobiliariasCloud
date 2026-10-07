package cl.inmobiliarias.service.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de permisos que antes vivia en el `PropiedadAuthInterceptor` del
 * api-gateway de Spring Boot y que ahora aplica el microservicio.
 *
 * En estas pruebas la cabecera `X-User` representa lo que API Gateway inyecta
 * despues de validar el access token: en esta capa ya no se revisa ninguna
 * firma, solo se decide que puede hacer cada rol.
 *
 * Corre con `seguridad.autorizacion-requerida=false` (perfil local), donde
 * permitirse el acceso sin identidad es lo esperado porque no hay API Gateway
 * delante. El comportamiento de produccion se cubre en
 * IdentidadInterceptorProduccionTest.
 *
 * @Transactional hace que cada test se revierta al terminar. La base H2 es una
 * sola para todos los tests del modulo (jdbc:h2:mem:inmobiliarias con
 * DB_CLOSE_DELAY=-1), asi que sin rollback las propiedades que crean estos tests
 * se filtrarian a PropiedadControllerIT, que espera exactamente 3.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IdentidadInterceptorTest {

    private static final String ADMIN = "administrador@inmobiliariaduoc.onmicrosoft.com";
    private static final String CORREDOR = "Corredor@InmobiliariaDuoc.onmicrosoft.com";
    private static final String DESCONOCIDO = "desconocido@ejemplo.com";

    @Autowired
    private MockMvc mockMvc;

    private static final String CUERPO_VALIDO = """
            {
              "titulo": "Casa en Vitacura",
              "categoria": "Casa",
              "tipoOperacion": "Venta",
              "precio": 250000000,
              "tipoPrecio": "/ Valor Total",
              "gastosComunes": 60000,
              "ubicacion": "Vitacura, Santiago",
              "piezas": 5,
              "banos": 4,
              "estacionamiento": 2,
              "bodega": 1,
              "videoUrl": "",
              "imagenes": ["imagenes/casa1.jpg"]
            }
            """;

    @Test
    void listarElCatalogoEsPublico() throws Exception {
        mockMvc.perform(get("/api/propiedades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())));
    }

    @Test
    void obtenerUnaPropiedadEsPublico() throws Exception {
        mockMvc.perform(get("/api/propiedades/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void elAdminPuedeCrear() throws Exception {
        mockMvc.perform(post("/api/propiedades")
                        .header("X-User", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Casa en Vitacura"));
    }

    @Test
    void elCorredorPuedeCrear() throws Exception {
        mockMvc.perform(post("/api/propiedades")
                        .header("X-User", CORREDOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isCreated());
    }

    @Test
    void elCorredorPuedeEliminar() throws Exception {
        mockMvc.perform(delete("/api/propiedades/3").header("X-User", CORREDOR))
                .andExpect(status().isNoContent());
    }

    @Test
    void elAdminPuedeEditar() throws Exception {
        mockMvc.perform(put("/api/propiedades/2")
                        .header("X-User", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isOk());
    }

    @Test
    void elCorredorNoPuedeEditar() throws Exception {
        mockMvc.perform(put("/api/propiedades/2")
                        .header("X-User", CORREDOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("no editarlas")));
    }

    @Test
    void unUsuarioSinRolNoPuedeCrear() throws Exception {
        mockMvc.perform(post("/api/propiedades")
                        .header("X-User", DESCONOCIDO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("no tiene permisos")));
    }

    @Test
    void laAuditoriaEsSoloDelAdmin() throws Exception {
        mockMvc.perform(get("/api/auditoria").header("X-User", CORREDOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("Solo el administrador")));

        mockMvc.perform(get("/api/auditoria").header("X-User", ADMIN))
                .andExpect(status().isOk());
    }

    @Test
    void meDevuelveLaIdentidadYElRolResueltos() throws Exception {
        mockMvc.perform(get("/api/me").header("X-User", CORREDOR).header("X-User-Name", "Ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(CORREDOR)))
                .andExpect(jsonPath("$.name", is("Ana")))
                .andExpect(jsonPath("$.rol", is(IdentidadInterceptor.ROL_CORREDOR)))
                .andExpect(jsonPath("$.isAdmin", is(false)))
                .andExpect(jsonPath("$.isCorredor", is(true)));
    }

    @Test
    void elRolSeResuelvePorOidCuandoElCorreoNoCoincide() throws Exception {
        mockMvc.perform(get("/api/me")
                        .header("X-User", "alias@otro-dominio.com")
                        .header("X-User-Oid", "5ce11c6e-c796-41eb-b8ed-1872878cd197"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol", is(IdentidadInterceptor.ROL_ADMIN)))
                .andExpect(jsonPath("$.isAdmin", is(true)));
    }

    /**
     * Es la cadena completa que antes vivia en el gateway: la identidad que
     * API Gateway inyecta debe quedar registrada en la tabla de auditoria con el
     * rol correspondiente.
     */
    @Test
    void laAccionQuedaRegistradaConElUsuarioYElRol() throws Exception {
        String titulo = "Depto " + System.nanoTime();
        String cuerpo = CUERPO_VALIDO.replace("Casa en Vitacura", titulo);

        mockMvc.perform(post("/api/propiedades")
                        .header("X-User", CORREDOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/auditoria").header("X-User", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accion").value("CREAR"))
                .andExpect(jsonPath("$[0].propiedad").value(titulo))
                .andExpect(jsonPath("$[0].usuario").value(CORREDOR))
                .andExpect(jsonPath("$[0].rol").value(IdentidadInterceptor.ROL_CORREDOR));
    }
}
