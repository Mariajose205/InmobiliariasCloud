package cl.inmobiliarias.service.service.messaging;

import cl.inmobiliarias.service.config.kafka.KafkaProperties;
import cl.inmobiliarias.service.dto.PropiedadEliminadaEvent;
import cl.inmobiliarias.service.model.Propiedad;
import cl.inmobiliarias.service.util.Comunas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Publica el evento {@link PropiedadEliminadaEvent} en el topic
 * {@code propiedades-eliminadas} cuando se borra una propiedad.
 *
 * Lo consume el wishlist-service, que:
 *   1. elimina la propiedad de los favoritos de todos los usuarios y
 *   2. deja un aviso por usuario ("ya fue vendida/arrendada") visible en la pagina.
 *
 * El envio es asincrono y nunca bloquea la respuesta HTTP: si Kafka falla, el
 * DELETE ya se hizo y el error solo queda en el log.
 */
@Service
public class PropiedadEliminadaPublisher {

    private static final Logger log = LoggerFactory.getLogger(PropiedadEliminadaPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaProperties properties;

    public PropiedadEliminadaPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                       KafkaProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void publicar(Propiedad propiedad) {
        if (propiedad == null || propiedad.getId() == null) {
            return;
        }

        PropiedadEliminadaEvent evento = new PropiedadEliminadaEvent();
        evento.setPropiedadId(propiedad.getId());
        evento.setTitulo(propiedad.getTitulo());
        evento.setTipoOperacion(propiedad.getTipoOperacion());
        evento.setUbicacion(propiedad.getUbicacion());

        String comuna = Comunas.extraerComuna(propiedad.getUbicacion());
        evento.setComuna(comuna);

        String topic = properties.getTopics().getPropiedadesEliminadas();
        String clave = comuna != null ? comuna : "OTROS";

        log.info("Publicando PropiedadEliminada en {} id={} titulo='{}' clave={}",
                topic, evento.getPropiedadId(), evento.getTitulo(), clave);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, clave, evento);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                // No se relanza: la propiedad ya se borro y la respuesta HTTP
                // ya salio. El error queda en el log para reprocesar a mano.
                log.error("Error al publicar PropiedadEliminada id={} en {}: {}",
                        evento.getPropiedadId(), topic, ex.getMessage(), ex);
            } else {
                log.info("PropiedadEliminada publicada en {}-{} (offset={}) id={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        evento.getPropiedadId());
            }
        });
    }
}
