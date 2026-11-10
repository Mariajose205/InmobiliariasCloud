package cl.inmobiliarias.service.service.messaging;

import cl.inmobiliarias.service.config.rabbitmq.RabbitProperties;
import cl.inmobiliarias.service.dto.AuditoriaEventoMsg;
import cl.inmobiliarias.service.model.Propiedad;
import cl.inmobiliarias.service.util.Comunas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Publica los eventos del dominio "propiedades" en el exchange topic
 * `inmobiliaria.topic`, con una routing key por accion:
 *
 *   propiedades.eventos.creada | .editar | .eliminar
 *
 * La cola `cola.propiedades.auditoria` esta ligada al exchange con el patron
 * propiedades.eventos.#, asi que recibe las tres acciones sin que el
 * publicador conozca la cola (acoplamiento por patron de routing key).
 *
 * Igual que el resto de los publicadores, un fallo del broker NO debe romper
 * la operacion HTTP original: el llamador envuelve la llamada en try/catch.
 */
@Service
public class AuditoriaEventosPublisher {

    public static final String ACCION_CREAR = "CREAR";
    public static final String ACCION_EDITAR = "EDITAR";
    public static final String ACCION_ELIMINAR = "ELIMINAR";

    private static final Logger log = LoggerFactory.getLogger(AuditoriaEventosPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties propiedades;

    public AuditoriaEventosPublisher(RabbitTemplate rabbitTemplate, RabbitProperties propiedades) {
        this.rabbitTemplate = rabbitTemplate;
        this.propiedades = propiedades;
    }

    public void publicar(Propiedad propiedad, String accion, String usuario, String rol) {
        if (propiedad == null || propiedad.getId() == null) {
            return;
        }

        AuditoriaEventoMsg evento = new AuditoriaEventoMsg(
                accion,
                propiedad.getId(),
                propiedad.getTitulo(),
                Comunas.extraerComuna(propiedad.getUbicacion()),
                usuario == null ? "" : usuario,
                rol == null ? "" : rol,
                Instant.now().toString()
        );

        String topic = propiedades.getExchanges().getTopic();
        String routingKey = routingKey(accion);

        rabbitTemplate.convertAndSend(topic, routingKey, evento);
        log.info("[Auditoria] Evento {} de la propiedad {} publicado en {} (rk={})",
                accion, evento.propiedadId(), topic, routingKey);
    }

    /** Routing key del exchange topic para cada accion del dominio. */
    private String routingKey(String accion) {
        return switch (accion == null ? "" : accion) {
            case ACCION_CREAR -> propiedades.getRoutingKeys().getPropiedadesEventosCrear();
            case ACCION_EDITAR -> propiedades.getRoutingKeys().getPropiedadesEventosEditar();
            case ACCION_ELIMINAR -> propiedades.getRoutingKeys().getPropiedadesEventosEliminar();
            default -> throw new IllegalArgumentException("Accion de auditoria desconocida: " + accion);
        };
    }
}
