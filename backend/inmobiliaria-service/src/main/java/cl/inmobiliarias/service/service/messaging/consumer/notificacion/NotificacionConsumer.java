package cl.inmobiliarias.service.service.messaging.consumer.notificacion;

import cl.inmobiliarias.service.dto.InformeSolicitudMsg;
import cl.inmobiliarias.service.service.messaging.Reintentos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Consumidor del dominio NOTIFICACIONES.
 *
 * Escucha `cola.correos.notificaciones` (alimentada por el exchange fanout
 * `inmobiliaria.fanout`) y avisa al usuario que su pedido de informe esta
 * siendo procesado.
 *
 * Estrategia de confirmacion y errores (ACK automatico, configurado con
 * `spring.rabbitmq.listener.simple.acknowledge-mode=auto`):
 *
 *   - EXITO: el contenedor confirma el mensaje automaticamente.
 *   - ERROR TRANSITORIO (SMTP caido, timeout, etc.): se reintenta con backoff
 *     (Reintentos). Agotados los intentos se lanza
 *     AmqpRejectAndDontRequeueException y el mensaje cae en la DLQ
 *     `cola.correos.notificaciones.dlq` (ruteada por `inmobiliaria.dlx`).
 *
 * Los errores que escapan al metodo (p.ej. conversion del payload) tambien se
 * rechazan sin requeue por el contenedor (default-requeue-rejected=false) y
 * terminan en la DLQ.
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
    public void consumir(InformeSolicitudMsg mensaje) {
        log.info("[Notificacion] Recibido aviso para {} (rol={}, solicitud={})",
                mensaje.solicitadoPorEmail(), mensaje.rol(), mensaje.solicitadoEn());
        try {
            Reintentos.conReintentos("Notificacion", MAX_INTENTOS, INTERVALO_INICIAL_MS, MULTIPLICADOR,
                    log, () -> enviarAvisoProcesando(mensaje));
            log.info("[Notificacion] Aviso enviado a {} (ACK automatico del contenedor).",
                    mensaje.solicitadoPorEmail());
        } catch (Exception ex) {
            log.error("[Notificacion] FALLO definitivo para {}: se rechaza sin requeue y el mensaje ira a la DLQ. Causa: {}",
                    mensaje.solicitadoPorEmail(), ex.getMessage());
            throw new AmqpRejectAndDontRequeueException(ex);
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
