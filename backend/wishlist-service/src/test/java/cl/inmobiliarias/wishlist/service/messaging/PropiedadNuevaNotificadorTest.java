package cl.inmobiliarias.wishlist.service.messaging;

import cl.inmobiliarias.wishlist.dto.NotificacionSistemaEvent;
import cl.inmobiliarias.wishlist.dto.PropiedadCreadaEvent;
import cl.inmobiliarias.wishlist.model.AlertaComuna;
import cl.inmobiliarias.wishlist.repository.AlertaComunaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.ack.Acknowledgment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del notificador con repositorio y publisher mockeados (sin Kafka real).
 */
@ExtendWith(MockitoExtension.class)
class PropiedadNuevaNotificadorTest {

    @Mock
    private AlertaComunaRepository alertaComunaRepository;

    @Mock
    private NotificacionKafkaPublisher publisher;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private PropiedadNuevaNotificador notificador;

    @Test
    void publicaNotificacionPorCadaAlertaDeLaComuna() {
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

        notificador.recibir(evento, "Quillota", ack, null);

        ArgumentCaptor<NotificacionSistemaEvent> captor = ArgumentCaptor.forClass(NotificacionSistemaEvent.class);
        verify(publisher, times(2)).publicar(captor.capture());
        assertEquals("u1@x.cl", captor.getAllValues().get(0).getDestinatarioEmail());
        assertEquals("u2@x.cl", captor.getAllValues().get(1).getDestinatarioEmail());
        assertEquals("Quillota", captor.getAllValues().get(0).getComuna());
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

        notificador.recibir(evento, null, ack, null);

        verify(publisher).publicar(org.mockito.ArgumentMatchers.any(NotificacionSistemaEvent.class));
        verify(ack).acknowledge();
    }

    @Test
    void eventoInvalidoSeDescartaSinPublicarYConfirma() {
        // Titulo en blanco: debe descartarse.
        PropiedadCreadaEvent evento = new PropiedadCreadaEvent();
        evento.setPropiedadId(12L);
        evento.setTitulo("  ");

        notificador.recibir(evento, "Quillota", ack, null);

        verify(publisher, never()).publicar(org.mockito.ArgumentMatchers.any());
        verify(alertaComunaRepository, never()).findByComunaIgnoreCase(org.mockito.ArgumentMatchers.anyString());
        verify(ack).acknowledge();
    }

    @Test
    void eventoSinPropiedadIdSeDescarta() {
        PropiedadCreadaEvent evento = new PropiedadCreadaEvent();
        evento.setTitulo("Casa");

        notificador.recibir(evento, "Quillota", ack, null);

        verify(publisher, never()).publicar(org.mockito.ArgumentMatchers.any());
        verify(ack).acknowledge();
    }

    @Test
    void sinAlertasNoPublicaNadaPeroConfirma() {
        PropiedadCreadaEvent evento = new PropiedadCreadaEvent();
        evento.setPropiedadId(13L);
        evento.setTitulo("Casa");
        evento.setComuna("Villa Alemana");

        when(alertaComunaRepository.findByComunaIgnoreCase(eq("Villa Alemana"))).thenReturn(List.of());

        notificador.recibir(evento, "Villa Alemana", ack, null);

        verify(publisher, never()).publicar(org.mockito.ArgumentMatchers.any());
        verify(ack).acknowledge();
    }
}
