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
 * Publica resumenes de notificaciones en el topic notificaciones-sistema.
 *
 * La clave Kafka es el correo del destinatario: todos los resumenes de un
 * mismo usuario caen en la misma particion y se consumen en orden. Asi dos
 * resumenes del mismo usuario nunca se envian desordenados.
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
    public void publicarResumen(NotificacionSistemaEvent evento) {
        String topic = properties.getTopics().getNotificacionesSistema();
        String clave = evento.getDestinatarioEmail();
        log.info("Publicando resumen en {} para {} ({} propiedades)",
                topic, evento.getDestinatarioEmail(),
                evento.getItems() == null ? 0 : evento.getItems().size());
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, clave, evento);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Error al publicar resumen para {} en {}: {}",
                        evento.getDestinatarioEmail(), topic, ex.getMessage(), ex);
            } else {
                log.info("Resumen publicado en {}-{} (offset={}) para {}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        evento.getDestinatarioEmail());
            }
        });
    }
}
