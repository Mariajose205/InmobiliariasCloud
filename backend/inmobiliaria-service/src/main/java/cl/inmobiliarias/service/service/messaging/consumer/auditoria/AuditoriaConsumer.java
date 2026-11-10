package cl.inmobiliarias.service.service.messaging.consumer.auditoria;

import cl.inmobiliarias.service.dto.AuditoriaEventoMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Consumidor del dominio AUDITORIA (tercer flujo del broker).
 *
 * Escucha `cola.propiedades.auditoria`, alimentada por el exchange topic
 * `inmobiliaria.topic` con el binding de patron propiedades.eventos.#, y
 * registra la bitacora legible de lo que ocurre con las propiedades.
 *
 * Estrategia de confirmacion y errores (igual que los otros consumidores,
 * `acknowledge-mode=auto` y `default-requeue-rejected=false`):
 *
 *   - EXITO: el contenedor confirma el mensaje automaticamente (ACK).
 *   - PAYLOAD INVALIDO (accion desconocida o propiedadId ausente): se rechaza
 *     sin requeue y el DLX `inmobiliaria.dlx` mueve el mensaje a
 *     `cola.propiedades.auditoria.dlq` (routing key propiedades.auditoria.dlq).
 *     Este es el camino que demuestra la redireccion de fallidos a la DLQ.
 */
@Component
public class AuditoriaConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaConsumer.class);

    private static final Set<String> ACCIONES_VALIDAS =
            Set.of("CREAR", "EDITAR", "ELIMINAR");

    @RabbitListener(queues = "${rabbitmq.queues.propiedades-auditoria}")
    public void consumir(AuditoriaEventoMsg mensaje) {
        if (mensaje == null
                || mensaje.accion() == null
                || !ACCIONES_VALIDAS.contains(mensaje.accion())
                || mensaje.propiedadId() == null) {
            log.error("[Auditoria] Mensaje invalido ({}): se rechaza sin requeue y ira a la DLQ. Cuerpo: {}",
                    mensaje == null ? "null" : mensaje.accion(), mensaje);
            throw new AmqpRejectAndDontRequeueException("Evento de auditoria invalido");
        }

        log.info("[Auditoria] {} propiedad {} ('{}', comuna={}) por {} (rol={}) en {}",
                mensaje.accion(),
                mensaje.propiedadId(),
                mensaje.titulo(),
                mensaje.comuna(),
                mensaje.usuario(),
                mensaje.rol(),
                mensaje.instantIso());
    }
}
