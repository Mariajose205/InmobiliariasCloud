package cl.inmobiliarias.wishlist.service.messaging;

import cl.inmobiliarias.wishlist.config.kafka.KafkaProperties;
import cl.inmobiliarias.wishlist.dto.NotificacionSistemaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Publica notificaciones en el topic notificaciones-sistema.
 *
 * La clave Kafka es la comuna, para que el consumo y el envio de correos
 * de una misma comuna quede ordenado por particion.
 */
@Service
public class NotificacionKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificacionKafkaPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaProperties properties;

    public NotificacionKafkaPublisher(KafkaTemplate<String, Object> kafkaTemplate, KafkaProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    /** Publica sin lanzar excepcion: el error queda solo en el log. */
    public void publicar(NotificacionSistemaEvent evento) {
        String topic = properties.getTopics().getNotificacionesSistema();
        String clave = evento.getComuna();
        log.info("Publicando notificacion en {} para {} (comuna={})",
                topic, evento.getDestinatarioEmail(), clave);
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, clave, evento);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Error al publicar notificacion para {} en {}: {}",
                        evento.getDestinatarioEmail(), topic, ex.getMessage(), ex);
            } else {
                log.info("Notificacion publicada en {}-{} (offset={}) para {}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        evento.getDestinatarioEmail());
            }
        });
    }
}
