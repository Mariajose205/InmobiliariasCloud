package cl.inmobiliarias.service.service.messaging.consumer.notificacion;

import cl.inmobiliarias.service.dto.InformeSolicitudMsg;
import cl.inmobiliarias.service.service.messaging.Reintentos;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor del dominio NOTIFICACIONES.
 *
 * Escucha `cola.correos.notificaciones` (alimentada por el exchange fanout
 * `inmobiliaria.fanout`) y avisa al usuario que su pedido de informe esta
 * siendo procesado.
 *
 * Estrategia de confirmacion y errores (ACK manual, configurado con
 * `spring.rabbitmq.listener.simple.acknowledge-mode=manual`):
 *
 *   - EXITO: se confirma con basicAck y el mensaje sale de la cola.
 *   - ERROR TRANSITORIO (SMTP caido, timeout, etc.): se reintenta con backoff
 *     (Reintentos). Agotados los reintentos, se hace
 *     basicNack(requeue=false) y el mensaje cae en la DLQ
 *     `cola.correos.notificaciones.dlq` (ruteada por `inmobiliaria.dlx`).
 *
 * Los errores que escapan al metodo (p.ej. conversion del payload) los NACKa
 * el DlqErrorHandler configurado en el contenedor de listeners.
 */
@Component
public class NotificacionConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacionConsumer.class);

    private static final int MAX_INTENTOS = 3;
    private static final long INTERVALO_INICIAL_MS = 2000L;
    private static final double MULTIPLICADOR = 2.0;

    private final JavaMailSender mailSender;

    public NotificacionConsumer(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @RabbitListener(queues = "${rabbitmq.queues.correos-notificaciones}")
    public void consumir(InformeSolicitudMsg mensaje,
                         Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("[Notificacion] Recibido aviso para {} (rol={}, solicitud={})",
                mensaje.solicitadoPorEmail(), mensaje.rol(), mensaje.solicitadoEn());
        try {
            Reintentos.conReintentos("Notificacion", MAX_INTENTOS, INTERVALO_INICIAL_MS, MULTIPLICADOR,
                    log, () -> enviarAvisoProcesando(mensaje));
            // ACK explicito: solo confirmamos cuando el correo se envio.
            channel.basicAck(deliveryTag, false);
            log.info("[Notificacion] ACK tag={} (aviso enviado a {})",
                    deliveryTag, mensaje.solicitadoPorEmail());
        } catch (Exception ex) {
            // Reintentos agotados: NACK(requeue=false) para que el DLX mueva a la DLQ.
            log.error("[Notificacion] FALLO definitivo (tag={}): NACK a DLQ. Causa: {}",
                    deliveryTag, ex.getMessage());
            nack(deliveryTag, channel);
        }
    }

    private void nack(long deliveryTag, Channel channel) {
        try {
            channel.basicNack(deliveryTag, false, false);
        } catch (IOException ex) {
            log.error("[Notificacion] No fue posible hacer NACK del tag={}: {}", deliveryTag, ex.getMessage(), ex);
        }
    }

    private void enviarAvisoProcesando(InformeSolicitudMsg mensaje) {
        SimpleMailMessage correo = new SimpleMailMessage();
        correo.setTo(mensaje.solicitadoPorEmail());
        correo.setSubject("Tu informe de propiedades esta siendo procesado");
        correo.setText("Hola,\n\n"
                + "Recibimos tu solicitud de informe de propiedades ("
                + mensaje.solicitadoEn() + ").\n"
                + "Estamos generando el PDF; te lo enviaremos en cuanto este listo.\n\n"
                + "Equipo Inmobiliarias Duoc");
        correo.setFrom("no-reply@inmobiliariaduoc.onmicrosoft.com");
        getMailSender().send(correo);
    }

    protected JavaMailSender getMailSender() {
        return mailSender;
    }
}
