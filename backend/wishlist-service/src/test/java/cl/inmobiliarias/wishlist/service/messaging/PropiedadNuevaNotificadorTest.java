package cl.inmobiliarias.wishlist.service.messaging;

import cl.inmobiliarias.wishlist.dto.PropiedadCreadaEvent;
import cl.inmobiliarias.wishlist.model.AlertaComuna;
import cl.inmobiliarias.wishlist.model.Novedad;
import cl.inmobiliarias.wishlist.repository.AlertaComunaRepository;
import cl.inmobiliarias.wishlist.repository.NovedadRepository;
import cl.inmobiliarias.wishlist.service.ResumenNovedadesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del notificador: ahora solo acumula novedades pendientes y delega en
 * ResumenNovedadesService la decision de enviar el resumen (sin Kafka real).
 */
@ExtendWith(MockitoExtension.class)
class PropiedadNuevaNotificadorTest {

    @Mock
    private AlertaComunaRepository alertaComunaRepository;

    @Mock
    private NovedadRepository novedadRepository;

    @Mock
    private ResumenNovedadesService resumenService;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private PropiedadNuevaNotificador notificador;

    @Test
    void guardaUnaNovedadPendientePorAlertaYCompruebaUmbral() {
        PropiedadCreadaEvent evento = new PropiedadCreadaEvent();
        evento.setPropiedadId(10L);
        evento.setTitulo("Casa en Quillota");
        evento.setComuna("Quillota");
        evento.setPrecio(150000L);
        evento.setTipoPrecio("CLP");
        evento.setUbicacion("Quillota centro");

        AlertaComuna a1 = new AlertaComuna("u1@x.cl", "oid1", "Quillota");
        AlertaComuna a2 = new AlertaComuna("u2@x.cl", "oid2", "Quillota");
        when(alertaComunaRepository.findByComunaIgnoreCase(eq("Quillota"))).thenReturn(List.of(a1, a2));
        when(novedadRepository.save(any(Novedad.class))).thenAnswer(inv -> inv.getArgument(0));

        notificador.recibir(evento, "Quillota", ack, null);

        ArgumentCaptor<Novedad> captor = ArgumentCaptor.forClass(Novedad.class);
        verify(novedadRepository, times(2)).save(captor.capture());
        assertEquals("u1@x.cl", captor.getAllValues().get(0).getUsuarioEmail());
        assertEquals("u2@x.cl", captor.getAllValues().get(1).getUsuarioEmail());
        assertEquals("Quillota", captor.getAllValues().get(0).getComuna());
        // El resumen se intenta para cada usuario afectado.
        verify(resumenService).crearSiAlcanzaUmbral("u1@x.cl", "oid1");
        verify(resumenService).crearSiAlcanzaUmbral("u2@x.cl", "oid2");
        verify(ack).acknowledge();
    }

    @Test
    void usaComunaDelEventoSiLaClaveEsNull() {
        PropiedadCreadaEvent evento = new PropiedadCreadaEvent();
        evento.setPropiedadId(11L);
        evento.setTitulo("Depto");
        evento.setComuna("Calera");

        AlertaComuna a1 = new AlertaComuna("u1@x.cl", "oid1", "Calera");
        when(alertaComunaRepository.findByComunaIgnoreCase(eq("Calera"))).thenReturn(List.of(a1));
        when(novedadRepository.save(any(Novedad.class))).thenAnswer(inv -> inv.getArgument(0));

        notificador.recibir(evento, null, ack, null);

        verify(novedadRepository).save(any(Novedad.class));
        verify(resumenService).crearSiAlcanzaUmbral(eq("u1@x.cl"), eq("oid1"));
        verify(ack).acknowledge();
    }

    @Test
    void siElGuardadoDuplicadoFallaIgualIntentaElResumen() {
        PropiedadCreadaEvent evento = new PropiedadCreadaEvent();
        evento.setPropiedadId(15L);
        evento.setTitulo("Casa repetida");
        evento.setComuna("Quillota");

        AlertaComuna a1 = new AlertaComuna("u1@x.cl", "oid1", "Quillota");
        when(alertaComunaRepository.findByComunaIgnoreCase(eq("Quillota"))).thenReturn(List.of(a1));
        when(novedadRepository.save(any(Novedad.class))).thenThrow(new RuntimeException("duplicado"));

        notificador.recibir(evento, "Quillota", ack, null);

        verify(resumenService).crearSiAlcanzaUmbral("u1@x.cl", "oid1");
        verify(ack).acknowledge();
    }

    @Test
    void eventoInvalidoSeDescartaSinGuardarNiResumir() {
        PropiedadCreadaEvent evento = new PropiedadCreadaEvent();
        evento.setPropiedadId(12L);
        evento.setTitulo("  ");

        notificador.recibir(evento, "Quillota", ack, null);

        verify(novedadRepository, never()).save(any());
        verify(alertaComunaRepository, never()).findByComunaIgnoreCase(any());
        verify(resumenService, never()).crearSiAlcanzaUmbral(any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void eventoSinPropiedadIdSeDescarta() {
        PropiedadCreadaEvent evento = new PropiedadCreadaEvent();
        evento.setTitulo("Casa");

        notificador.recibir(evento, "Quillota", ack, null);

        verify(novedadRepository, never()).save(any());
        verify(resumenService, never()).crearSiAlcanzaUmbral(any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void sinAlertasNoGuardaNadaPeroConfirma() {
        PropiedadCreadaEvent evento = new PropiedadCreadaEvent();
        evento.setPropiedadId(13L);
        evento.setTitulo("Casa");
        evento.setComuna("Villa Alemana");

        when(alertaComunaRepository.findByComunaIgnoreCase(eq("Villa Alemana"))).thenReturn(List.of());

        notificador.recibir(evento, "Villa Alemana", ack, null);

        verify(novedadRepository, never()).save(any());
        verify(resumenService, never()).crearSiAlcanzaUmbral(any(), any());
        verify(ack).acknowledge();
    }
}
