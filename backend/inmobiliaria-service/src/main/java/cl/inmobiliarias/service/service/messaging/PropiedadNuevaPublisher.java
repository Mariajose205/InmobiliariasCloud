package cl.inmobiliarias.service.service.messaging;

import cl.inmobiliarias.service.config.kafka.KafkaProperties;
import cl.inmobiliarias.service.dto.PropiedadCreadaEvent;
import cl.inmobiliarias.service.model.Propiedad;
import cl.inmobiliarias.service.util.Comunas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Publica el evento PropiedadCreadaEvent en el topic propiedades-nuevas.
 *
 * Se invoca justo despues de crear la propiedad (PropiedadController.crear).
 * La clave Kafka es la comuna canonica extraida de la ubicacion, o "OTROS"
 * si no se reconoce. El wishlist-service consume este topic y avisa a los
 * usuarios que siguen esa comuna (correo + novedad en pagina principal).
 *
 * El envio es asincrono y nunca bloquea la respuesta HTTP: si Kafka falla,
 * solo queda registrado en el log y la propiedad ya creada sigue existiendo.
 */
@Service
public class PropiedadNuevaPublisher {

    private static final Logger log = LoggerFactory.getLogger(PropiedadNuevaPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaProperties properties;

    public PropiedadNuevaPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                   KafkaProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void publicar(Propiedad propiedad) {
        if (propiedad == null || propiedad.getId() == null) {
            return;
        }
        PropiedadCreadaEvent evento = new PropiedadCreadaEvent();
        evento.setPropiedadId(propiedad.getId());
        evento.setTitulo(propiedad.getTitulo());
        evento.setCategoria(propiedad.getCategoria());
        evento.setTipoOperacion(propiedad.getTipoOperacion());
        evento.setPrecio(propiedad.getPrecio());
        evento.setTipoPrecio(propiedad.getTipoPrecio());
        evento.setUbicacion(propiedad.getUbicacion());
        evento.setCreadoPorEmail(propiedad.getCreadoPorEmail());
        if (propiedad.getFechaCreacion() != null) {
            evento.setFechaCreacion(propiedad.getFechaCreacion().toString());
        }

        String comuna = Comunas.extraerComuna(propiedad.getUbicacion());
        evento.setComuna(comuna);

        String topic = properties.getTopics().getPropiedadesNuevas();
        String clave = comuna != null ? comuna : "OTROS";

        log.info("Publicando PropiedadCreada en {} id={} titulo='{}' clave={}",
                topic, evento.getPropiedadId(), evento.getTitulo(), clave);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, clave, evento);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                // No se relanza: el catalogo no debe fallar porque Kafka este caido.
                // El error queda en el log para reprocessing manual si se requiere.
                log.error("Error al publicar PropiedadCreada id={} en {}: {}",
                        evento.getPropiedadId(), topic, ex.getMessage(), ex);
            } else {
                log.info("PropiedadCreada publicada en {}-{} (offset={}) id={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        evento.getPropiedadId());
            }
        });
    }
}
