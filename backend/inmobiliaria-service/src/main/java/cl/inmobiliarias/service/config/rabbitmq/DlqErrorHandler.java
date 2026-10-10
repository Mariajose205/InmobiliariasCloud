package cl.inmobiliarias.service.config.rabbitmq;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;

import java.io.IOException;

/**
 * ErrorHandler del contenedor de listeners.
 *
 * Se ejecuta cuando un error escapa del metodo del consumidor (p.ej. fallo de
 * conversion de un payload invalido en la cola). Extrae el Channel y el
 * delivery tag del mensaje fallido y hace NACK(requeue=false), de modo que el
 * DLX mueva el mensaje a su DLQ en lugar de dejarlo colgado en Unacked.
 */
@Component
public class DlqErrorHandler implements ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(DlqErrorHandler.class);

    @Override
    public void handleError(Throwable t) {
        Message mensaje = mensajeFallido(t);
        boolean nackHecho = false;
        if (mensaje != null && mensaje.getMessageProperties() != null) {
            Object channelObj = mensaje.getMessageProperties().getHeader(AmqpHeaders.CHANNEL);
            Object tagObj = mensaje.getMessageProperties().getHeader(AmqpHeaders.DELIVERY_TAG);
            if (channelObj instanceof Channel channel && tagObj instanceof Number tag) {
                long deliveryTag = tag.longValue();
                try {
                    channel.basicNack(deliveryTag, false, false);
                    log.error("[DLQ] Error no manejado por el consumidor: NACK a DLQ (tag={}, causa={})",
                            deliveryTag, causaRaiz(t).getMessage());
                    nackHecho = true;
                } catch (IOException ex) {
                    log.error("[DLQ] No fue posible hacer NACK del tag={}: {}", deliveryTag, ex.getMessage(), ex);
                }
            }
        }
        if (!nackHecho) {
            log.error("[DLQ] Error sin NACK posible: {}", causaRaiz(t).getMessage(), t);
        }
    }

    private Message mensajeFallido(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            if (cursor instanceof ListenerExecutionFailedException ex) {
                Message mensaje = ex.getFailedMessage();
                if (mensaje != null) {
                    return mensaje;
                }
            }
            cursor = cursor.getCause();
        }
        return null;
    }

    private Throwable causaRaiz(Throwable t) {
        Throwable cursor = t;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor;
    }
}