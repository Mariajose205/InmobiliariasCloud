package cl.inmobiliarias.wishlist.service;

import cl.inmobiliarias.wishlist.config.NotificacionesProperties;
import cl.inmobiliarias.wishlist.dto.NotificacionSistemaEvent;
import cl.inmobiliarias.wishlist.model.Novedad;
import cl.inmobiliarias.wishlist.model.ResumenNovedades;
import cl.inmobiliarias.wishlist.repository.NovedadRepository;
import cl.inmobiliarias.wishlist.repository.ResumenNovedadesRepository;
import cl.inmobiliarias.wishlist.service.messaging.NotificacionKafkaPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del agrupamiento de novedades (umbral 6 + barrido diario).
 */
@ExtendWith(MockitoExtension.class)
class ResumenNovedadesServiceTest {

    @Mock
    private NovedadRepository novedadRepository;

    @Mock
    private ResumenNovedadesRepository resumenRepository;

    @Mock
    private NotificacionKafkaPublisher publisher;

    @Mock
    private NotificacionesProperties propiedades;

    @InjectMocks
    private ResumenNovedadesService service;

    private Novedad novedad(String email, long propiedadId, String comuna) {
        return new Novedad(email, "oid-1", comuna, propiedadId,
                "Propiedad " + propiedadId, 100000L, "CLP", "Centro " + comuna, "Nueva propiedad");
    }

    private void guardarConId() {
        AtomicLong id = new AtomicLong(100);
        when(resumenRepository.save(any(ResumenNovedades.class))).thenAnswer(inv -> {
            ResumenNovedades resumen = inv.getArgument(0);
            resumen.setId(id.getAndIncrement());
            return resumen;
        });
    }

    @Test
    void noCreaResumenSiNoAlcanzaElUmbral() {
        when(propiedades.getUmbral()).thenReturn(6);
        when(novedadRepository.countByUsuarioEmailIgnoreCaseAndResumenIdIsNull("u@x.cl"))
                .thenReturn(5L);

        Optional<ResumenNovedades> resultado = service.crearSiAlcanzaUmbral("u@x.cl", "oid-1");

        assertTrue(resultado.isEmpty());
        verify(resumenRepository, never()).save(any());
        verify(publisher, never()).publicarResumen(any());
    }

    @Test
    void alLlegarAlUmbralCreaUnSoloResumenConSeisPropiedades() {
        when(propiedades.getUmbral()).thenReturn(6);
        when(novedadRepository.countByUsuarioEmailIgnoreCaseAndResumenIdIsNull("u@x.cl"))
                .thenReturn(6L);
        List<Novedad> items = List.of(
                novedad("u@x.cl", 1L, "Quillota"),
                novedad("u@x.cl", 2L, "Quillota"),
                novedad("u@x.cl", 3L, "Quillota"),
                novedad("u@x.cl", 4L, "Villa Alemana"),
                novedad("u@x.cl", 5L, "Villa Alemana"),
                novedad("u@x.cl", 6L, "Quillota"));
        when(novedadRepository.findByUsuarioEmailIgnoreCaseAndResumenIdIsNullOrderByFechaCreacionAsc(
                eq("u@x.cl"), any(Pageable.class))).thenReturn(items);
        guardarConId();

        Optional<ResumenNovedades> resultado = service.crearSiAlcanzaUmbral("u@x.cl", "oid-1");

        assertTrue(resultado.isPresent());
        assertEquals(6, resultado.get().getCantidad());
        assertEquals("u@x.cl", resultado.get().getUsuarioEmail());
        // Las 6 novedades quedan marcadas con el resumen creado.
        assertTrue(items.stream().allMatch(n -> Long.valueOf(100L).equals(n.getResumenId())));
        verify(novedadRepository).saveAll(items);

        ArgumentCaptor<NotificacionSistemaEvent> captor =
                ArgumentCaptor.forClass(NotificacionSistemaEvent.class);
        verify(publisher, times(1)).publicarResumen(captor.capture());
        assertEquals("u@x.cl", captor.getValue().getDestinatarioEmail());
        assertEquals(6, captor.getValue().getItems().size());
        assertEquals(List.of("Quillota", "Villa Alemana"), captor.getValue().getComunas());
    }

    @Test
    void elBarridoDiarioAgrupaTodoLoPendienteDeCadaUsuario() {
        when(novedadRepository.findUsuariosConPendientes()).thenReturn(List.of("a@x.cl", "b@x.cl"));
        when(novedadRepository.findByUsuarioEmailIgnoreCaseAndResumenIdIsNullOrderByFechaCreacionAsc("a@x.cl"))
                .thenReturn(List.of(novedad("a@x.cl", 1L, "Quillota"),
                        novedad("a@x.cl", 2L, "Quillota")));
        when(novedadRepository.findByUsuarioEmailIgnoreCaseAndResumenIdIsNullOrderByFechaCreacionAsc("b@x.cl"))
                .thenReturn(List.of(novedad("b@x.cl", 3L, "Villa Alemana")));
        guardarConId();

        int creados = service.generarResumenesPendientes();

        assertEquals(2, creados);
        verify(resumenRepository, times(2)).save(any(ResumenNovedades.class));
        ArgumentCaptor<NotificacionSistemaEvent> captor =
                ArgumentCaptor.forClass(NotificacionSistemaEvent.class);
        verify(publisher, times(2)).publicarResumen(captor.capture());
        assertEquals(2, captor.getAllValues().get(0).getCantidad());
        assertEquals(1, captor.getAllValues().get(1).getCantidad());
    }

    @Test
    void sinPendientesElBarridoNoHaceNada() {
        when(novedadRepository.findUsuariosConPendientes()).thenReturn(List.of());

        int creados = service.generarResumenesPendientes();

        assertEquals(0, creados);
        verify(resumenRepository, never()).save(any());
        verify(publisher, never()).publicarResumen(any());
        verify(novedadRepository, never()).findByUsuarioEmailIgnoreCaseAndResumenIdIsNullOrderByFechaCreacionAsc(anyString());
    }
}
