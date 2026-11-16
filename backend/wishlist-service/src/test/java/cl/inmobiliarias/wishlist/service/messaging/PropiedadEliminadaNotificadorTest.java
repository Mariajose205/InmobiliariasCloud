package cl.inmobiliarias.wishlist.service.messaging;

import cl.inmobiliarias.wishlist.dto.PropiedadEliminadaEvent;
import cl.inmobiliarias.wishlist.service.AvisoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests del consumidor de bajas: valida el evento y delega en AvisoService.
 */
@ExtendWith(MockitoExtension.class)
class PropiedadEliminadaNotificadorTest {

    @Mock
    private AvisoService avisoService;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private PropiedadEliminadaNotificador notificador;

    @Test
    void unEventoValidoSeProcesaYSeConfirma() {
        PropiedadEliminadaEvent evento = new PropiedadEliminadaEvent();
        evento.setPropiedadId(3L);
        evento.setTitulo("Casa");

        notificador.recibir(evento, ack);

        verify(avisoService).registrarEliminacion(evento);
        verify(ack).acknowledge();
    }

    @Test
    void unEventoSinIdSeDescartaYSeConfirma() {
        PropiedadEliminadaEvent evento = new PropiedadEliminadaEvent();
        evento.setTitulo("Casa");

        notificador.recibir(evento, ack);

        verify(avisoService, never()).registrarEliminacion(any());
        verify(ack).acknowledge();
    }

    @Test
    void unEventoNuloSeDescartaYSeConfirma() {
        notificador.recibir(null, ack);

        verify(avisoService, never()).registrarEliminacion(any());
        verify(ack).acknowledge();
    }
}
