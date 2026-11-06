package cl.inmobiliarias.wishlist.service.messaging;

import cl.inmobiliarias.wishlist.dto.NotificacionSistemaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Consumidor final del topic notificaciones-sistema: envia el correo.
 *
 * Si el envio falla se lanza la excepcion para que DefaultErrorHandler
 * reintente y, al agotar los intentos, mande el mensaje a
 * notificaciones-sistema.dlq.
 */
@Service
public class NotificacionDispatchConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacionDispatchConsumer.class);

    private final JavaMailSender mailSender;

    public NotificacionDispatchConsumer(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @KafkaListener(topics = "${app.kafka.topics.notificaciones-sistema}",
            groupId = "notificaciones-dispatch-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void recibir(NotificacionSistemaEvent evento, Acknowledgment ack) {
        // Sin destinatario valido no hay forma de avisar: se descarta.
        if (evento == null || evento.getDestinatarioEmail() == null
                || evento.getDestinatarioEmail().isBlank()) {
            log.warn("Notificacion sin destinatario, se descarta: {}", evento);
            if (ack != null) {
                ack.acknowledge();
            }
            return;
        }

        log.info("Enviando correo a {} (comuna={}, propiedadId={})",
                evento.getDestinatarioEmail(), evento.getComuna(), evento.getPropiedadId());

        String asunto = "Nueva propiedad en " + evento.getComuna();
        String texto = (evento.getMensaje() != null ? evento.getMensaje() : asunto) + "\n\n"
                + evento.getTitulo() + "\n"
                + (evento.getPrecio() != null ? "Precio: " + evento.getPrecio() + " " + (evento.getTipoPrecio() != null ? evento.getTipoPrecio() : "") + "\n" : "")
                + "Ubicacion: " + (evento.getUbicacion() != null ? evento.getUbicacion() : "");

        SimpleMailMessage correo = new SimpleMailMessage();
        correo.setTo(evento.getDestinatarioEmail());
        correo.setSubject(asunto);
        correo.setText(texto);

        try {
            mailSender.send(correo);
        } catch (Exception ex) {
            log.error("Error al enviar correo a {}: {}", evento.getDestinatarioEmail(), ex.getMessage(), ex);
            throw new RuntimeException("No se pudo enviar correo a " + evento.getDestinatarioEmail(), ex);
        }

        log.info("Correo enviado a {}", evento.getDestinatarioEmail());
        if (ack != null) {
            ack.acknowledge();
        }
    }
}
