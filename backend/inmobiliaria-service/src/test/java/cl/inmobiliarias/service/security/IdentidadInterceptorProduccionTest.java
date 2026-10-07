package cl.inmobiliarias.service.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comportamiento de `seguridad.autorizacion-requerida=true`, que es como corre
 * el microservicio en produccion.
 *
 * API Gateway siempre inyecta la identidad del usuario en las peticiones que
 * autoriza, de modo que una peticion sin `X-User` solo puede significar que
 * alguien alcanzo el microservicio por una via que no es el API Gateway. Por
 * eso se rechaza, en vez de degradar a rol PUBLIC.
 *
 * Las lecturas publicas del catalogo se mantienen abiertas: son las que pide
 * Home.jsx sin sesion iniciada.
 */
@SpringBootTest(properties = "seguridad.autorizacion-requerida=true")
@AutoConfigureMockMvc
@Transactional
class IdentidadInterceptorProduccionTest {

    private static final String ADMIN = "administrador@inmobiliariaduoc.onmicrosoft.com";

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

    @Autowired
    private MockMvc mockMvc;

    @Test
    void elCatalogoSigueSiendoPublico() throws Exception {
        mockMvc.perform(get("/api/propiedades"))
                .andExpect(status().isOk());
    }

    @Test
    void unaPeticionSinIdentidadSeRechaza() throws Exception {
        mockMvc.perform(post("/api/propiedades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("no fue validada por el API Gateway")));
    }

    @Test
    void editarSinIdentidadSeRechaza() throws Exception {
        mockMvc.perform(put("/api/propiedades/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void laAuditoriaSinIdentidadSeRechaza() throws Exception {
        mockMvc.perform(get("/api/auditoria"))
                .andExpect(status().isForbidden());
    }

    @Test
    void conIdentidadDelAdminTodoSigueFuncionando() throws Exception {
        mockMvc.perform(post("/api/propiedades")
                        .header("X-User", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/auditoria").header("X-User", ADMIN))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me").header("X-User", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAdmin").value(true));
    }
}
