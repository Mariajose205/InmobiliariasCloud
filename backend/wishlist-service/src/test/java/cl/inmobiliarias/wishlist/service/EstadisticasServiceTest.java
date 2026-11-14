package cl.inmobiliarias.wishlist.service;

import cl.inmobiliarias.wishlist.model.AlertaComuna;
import cl.inmobiliarias.wishlist.model.Favorito;
import cl.inmobiliarias.wishlist.model.ResumenNovedades;
import cl.inmobiliarias.wishlist.repository.AlertaComunaRepository;
import cl.inmobiliarias.wishlist.repository.FavoritoRepository;
import cl.inmobiliarias.wishlist.repository.ResumenNovedadesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Tests de las metricas del dashboard. No tocan base de datos: el servicio
 * expone {@code calcular(...)} para probar el calculo puro con listas.
 */
@ExtendWith(MockitoExtension.class)
class EstadisticasServiceTest {

    @Mock
    private FavoritoRepository favoritoRepository;

    @Mock
    private AlertaComunaRepository alertaRepository;

    @Mock
    private ResumenNovedadesRepository resumenRepository;

    @InjectMocks
    private EstadisticasService service;

    @Test
    void resumenCalculaTotalesYRankings() {
        List<Favorito> favoritos = List.of(
                favorito("ana@example.com", 1L, "Casa en Quillota", "Quillota"),
                favorito("ana@example.com", 2L, "Depto en Viña del Mar", "Viña del Mar"),
                favorito("beto@example.com", 1L, "Casa en Quillota", "Quillota"),
                favorito("beto@example.com", 1L, "Casa en Quillota", "Quillota")
        );
        List<AlertaComuna> alertas = List.of(
                alerta("ana@example.com", "Quillota"),
                alerta("ana@example.com", "Valparaíso"),
                alerta("beto@example.com", "Quillota")
        );
        List<ResumenNovedades> resumenes = List.of(
                resumen("ana@example.com", true),
                resumen("beto@example.com", false)
        );

        when(favoritoRepository.findAll()).thenReturn(favoritos);
        when(alertaRepository.findAll()).thenReturn(alertas);
        when(resumenRepository.findAll()).thenReturn(resumenes);

        Map<String, Object> resumen = service.resumen();

        // Totales y clientes distintos (los correos se comparan sin distinguir mayusculas)
        assertEquals(4, resumen.get("totalFavoritos"));
        assertEquals(2L, resumen.get("clientesConFavoritos"));
        assertEquals(3, resumen.get("totalAlertas"));
        assertEquals(2L, resumen.get("clientesConAlertas"));
        assertEquals(2, resumen.get("totalResumenes"));
        assertEquals(1L, resumen.get("resumenesEnviados"));

        // La propiedad 1 la guardaron 3 veces: encabeza el ranking
        List<Map<String, Object>> topPropiedades =
                (List<Map<String, Object>>) resumen.get("topPropiedadesGuardadas");
        assertEquals(2, topPropiedades.size());
        assertEquals(1L, topPropiedades.get(0).get("propiedadId"));
        assertEquals(3, topPropiedades.get(0).get("guardados"));
        assertEquals("Casa en Quillota", topPropiedades.get(0).get("titulo"));

        // Quillota es la comuna con mas favoritos y con mas alertas
        List<Map<String, Object>> topFavoritos =
                (List<Map<String, Object>>) resumen.get("topComunasFavoritos");
        assertEquals("Quillota", topFavoritos.get(0).get("comuna"));
        assertEquals(3L, topFavoritos.get(0).get("guardados"));

        List<Map<String, Object>> topAlertas =
                (List<Map<String, Object>>) resumen.get("topComunasAlertas");
        assertEquals("Quillota", topAlertas.get(0).get("comuna"));
        assertEquals(2L, topAlertas.get(0).get("seguidores"));
    }

    @Test
    void resumenVacioDevuelveCerosYListasVacias() {
        when(favoritoRepository.findAll()).thenReturn(List.of());
        when(alertaRepository.findAll()).thenReturn(List.of());
        when(resumenRepository.findAll()).thenReturn(List.of());

        Map<String, Object> resumen = service.resumen();

        assertEquals(0, resumen.get("totalFavoritos"));
        assertEquals(0L, resumen.get("clientesConFavoritos"));
        assertEquals(0, resumen.get("totalAlertas"));
        assertEquals(0L, resumen.get("clientesConAlertas"));
        assertEquals(0, resumen.get("totalResumenes"));
        assertEquals(0L, resumen.get("resumenesEnviados"));
        assertTrue(((List<?>) resumen.get("topPropiedadesGuardadas")).isEmpty());
        assertTrue(((List<?>) resumen.get("topComunasFavoritos")).isEmpty());
        assertTrue(((List<?>) resumen.get("topComunasAlertas")).isEmpty());
    }

    private Favorito favorito(String email, Long propiedadId, String titulo, String comuna) {
        Favorito favorito = new Favorito();
        favorito.setUsuarioEmail(email);
        favorito.setPropiedadId(propiedadId);
        favorito.setTitulo(titulo);
        favorito.setComuna(comuna);
        return favorito;
    }

    private AlertaComuna alerta(String email, String comuna) {
        return new AlertaComuna(email, null, comuna);
    }

    private ResumenNovedades resumen(String email, boolean enviado) {
        ResumenNovedades resumen = new ResumenNovedades();
        resumen.setUsuarioEmail(email);
        resumen.setCantidad(6);
        resumen.setComunas("Quillota");
        resumen.setMotivo("UMBRAL");
        resumen.setCorreoEnviado(enviado);
        return resumen;
    }
}
