package cl.inmobiliarias.service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PropiedadControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listarDevuelveLasPropiedadesSembradas() throws Exception {
        mockMvc.perform(get("/api/propiedades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].titulo").isNotEmpty());
    }

    @Test
    void obtenerDevuelvePropiedad() throws Exception {
        mockMvc.perform(get("/api/propiedades/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void crearAgregaUnaPropiedad() throws Exception {
        String body = """
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

        mockMvc.perform(post("/api/propiedades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.titulo").value("Casa en Vitacura"));
    }

    @Test
    void actualizarModificaUnaPropiedad() throws Exception {
        String body = """
                {
                  "titulo": "Casa Actualizada",
                  "categoria": "Casa",
                  "tipoOperacion": "Arriendo",
                  "precio": 1200000,
                  "tipoPrecio": "/ Mensual",
                  "gastosComunes": 40000,
                  "ubicacion": "Providencia",
                  "piezas": 3,
                  "banos": 2,
                  "estacionamiento": 1,
                  "bodega": 0,
                  "videoUrl": "",
                  "imagenes": ["imagenes/casa1.jpg"]
                }
                """;

        mockMvc.perform(put("/api/propiedades/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Casa Actualizada"))
                .andExpect(jsonPath("$.tipoOperacion").value("Arriendo"));
    }

    @Test
    void eliminarBorraUnaPropiedad() throws Exception {
        mockMvc.perform(delete("/api/propiedades/1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/propiedades/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void crearRechazaDatosInvalidos() throws Exception {
        String body = """
                {
                  "titulo": "",
                  "precio": -5
                }
                """;

        mockMvc.perform(post("/api/propiedades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
