package cl.inmobiliarias.wishlist.service.messaging;

import cl.inmobiliarias.wishlist.dto.NotificacionSistemaEvent;
import cl.inmobiliarias.wishlist.dto.PropiedadCreadaEvent;
import cl.inmobiliarias.wishlist.model.AlertaComuna;
import cl.inmobiliarias.wishlist.repository.AlertaComunaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.ack.Acknowledgment;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Consumidor del topic propiedades-nuevas.
 *
 * Por cada propiedad nueva busca las alertas de esa comuna y genera una
 * notificacion por usuario, que luego NotificacionDispatchConsumer
 * convierte en correo.
 */
@Service
public class PropiedadNuevaNotificador {

    private static final Logger log = LoggerFactory.getLogger(PropiedadNuevaNotificador.class);

    private final AlertaComunaRepository alertaComunaRepository;
    private final NotificacionKafkaPublisher publisher;

    public PropiedadNuevaNotificador(AlertaComunaRepository alertaComunaRepository,
                                     NotificacionKafkaPublisher publisher) {
        this.alertaComunaRepository = alertaComunaRepository;
        this.publisher = publisher;
    }

    @KafkaListener(topics = "${app.kafka.topics.propiedades-nuevas}",
            groupId = "wishlist-notifier-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void recibir(PropiedadCreadaEvent evento,
                        @Header(KafkaHeaders.RECEIVED_KEY) String comunaClave,
                        Acknowledgment ack, Message<?> message) {
        // Validacion defensiva: un evento incompleto se descarta sin reintento.
        if (evento == null || evento.getPropiedadId() == null
                || evento.getTitulo() == null || evento.getTitulo().isBlank()) {
            log.warn("Evento de propiedad nueva invalido recibido (clave={}), se descarta: {}", comunaClave, evento);
            if (ack != null) {
                ack.acknowledge();
            }
            return;
        }

        log.info("Recibida propiedad nueva id={} titulo='{}' clave={} comuna={}",
                evento.getPropiedadId(), evento.getTitulo(), comunaClave, evento.getComuna());

        // La comuna viene en la clave; si falta, se usa la del evento.
        String comuna = comunaClave != null && !comunaClave.isBlank()
                ? comunaClave : evento.getComuna();
        if (comuna == null || comuna.isBlank()) {
            log.warn("Propiedad id={} sin comuna, no se puede avisar a ningun usuario", evento.getPropiedadId());
            if (ack != null) {
                ack.acknowledge();
            }
            return;
        }

        List<AlertaComuna> alertas = alertaComunaRepository.findByComunaIgnoreCase(comuna);
        if (alertas.isEmpty()) {
            log.info("Nadie sigue la comuna {} (propiedad id={}), sin notificaciones",
                    comuna, evento.getPropiedadId());
        }
        for (AlertaComuna alerta : alertas) {
            NotificacionSistemaEvent notificacion = new NotificacionSistemaEvent();
            notificacion.setDestinatarioEmail(alerta.getUsuarioEmail());
            notificacion.setComuna(comuna);
            notificacion.setPropiedadId(evento.getPropiedadId());
            notificacion.setTitulo(evento.getTitulo());
            notificacion.setPrecio(evento.getPrecio());
            notificacion.setTipoPrecio(evento.getTipoPrecio());
            notificacion.setUbicacion(evento.getUbicacion());
            notificacion.setMensaje("Nueva propiedad en " + comuna + ": " + evento.getTitulo());
            publisher.publicar(notificacion);
            log.info("Notificacion generada para {} por propiedad id={} en {}",
                    alerta.getUsuarioEmail(), evento.getPropiedadId(), comuna);
        }

        if (ack != null) {
            ack.acknowledge();
        }
    }
}
