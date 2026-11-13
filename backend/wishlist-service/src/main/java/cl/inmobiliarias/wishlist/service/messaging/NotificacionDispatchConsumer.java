package cl.inmobiliarias.wishlist.service.messaging;

import cl.inmobiliarias.wishlist.dto.NotificacionItem;
import cl.inmobiliarias.wishlist.dto.NotificacionSistemaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Consumidor final del topic notificaciones-sistema: envia el correo.
 *
 * El formato normal es un RESUMEN (varios items en un solo correo) para no
 * saturar al usuario. Si llega un mensaje del formato anterior (sin items) se
 * envia igual como notificacion individual.
 *
 * Si el envio falla se lanza la excepcion para que DefaultErrorHandler
 * reintente y, al agotar los intentos, mande el mensaje a
 * notificaciones-sistema.dlq.
 */
@Service
public class NotificacionDispatchConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacionDispatchConsumer.class);

    /** Enlace publico del sitio, usado en el pie del correo. */
    private static final String URL_SITIO = "https://inmobiliariasduoc.duckdns.org";

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

        boolean esResumen = evento.getItems() != null && !evento.getItems().isEmpty();
        String asunto = esResumen ? asuntoResumen(evento) : asuntoIndividual(evento);
        String texto = esResumen ? textoResumen(evento) : textoIndividual(evento);

        log.info("Enviando {} a {} ({} propiedades)",
                esResumen ? "resumen" : "notificacion",
                evento.getDestinatarioEmail(),
                esResumen ? evento.getItems().size() : 1);

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

    private String asuntoResumen(NotificacionSistemaEvent evento) {
        int cantidad = evento.getCantidad() != null ? evento.getCantidad()
                : (evento.getItems() == null ? 0 : evento.getItems().size());
        String comunas = comunasEnTexto(evento.getComunas());
        String base = cantidad == 1 ? "1 nueva propiedad" : cantidad + " nuevas propiedades";
        return comunas.isBlank() ? base + " en tus comunas" : base + " en " + comunas;
    }

    private String textoResumen(NotificacionSistemaEvent evento) {
        List<NotificacionItem> items = evento.getItems();
        StringBuilder sb = new StringBuilder();
        sb.append("Se publicaron ").append(items.size())
                .append(items.size() == 1 ? " propiedad nueva" : " propiedades nuevas")
                .append(" en las comunas que sigues:\n\n");
        int i = 1;
        for (NotificacionItem item : items) {
            sb.append(i++).append(". ").append(textoItem(item)).append('\n');
        }
        sb.append("\nEntra a ").append(URL_SITIO).append(" para verlas.\n\n");
        sb.append("Recibes este resumen agrupado para no llenarte de correos.");
        return sb.toString();
    }

    private String textoItem(NotificacionItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.getTitulo() != null ? item.getTitulo() : "Propiedad");
        if (item.getComuna() != null && !item.getComuna().isBlank()) {
            sb.append(" (").append(item.getComuna()).append(')');
        }
        if (item.getPrecio() != null) {
            sb.append(" - ").append(formatearPrecio(item.getPrecio()));
            if (item.getTipoPrecio() != null && !item.getTipoPrecio().isBlank()) {
                sb.append(' ').append(item.getTipoPrecio());
            }
        }
        if (item.getUbicacion() != null && !item.getUbicacion().isBlank()) {
            sb.append(" - ").append(item.getUbicacion());
        }
        return sb.toString();
    }

    private String asuntoIndividual(NotificacionSistemaEvent evento) {
        return "Nueva propiedad en " + evento.getComuna();
    }

    private String textoIndividual(NotificacionSistemaEvent evento) {
        return (evento.getMensaje() != null ? evento.getMensaje() : asuntoIndividual(evento)) + "\n\n"
                + evento.getTitulo() + "\n"
                + (evento.getPrecio() != null ? "Precio: " + evento.getPrecio() + " "
                    + (evento.getTipoPrecio() != null ? evento.getTipoPrecio() : "") + "\n" : "")
                + "Ubicacion: " + (evento.getUbicacion() != null ? evento.getUbicacion() : "");
    }

    private String comunasEnTexto(List<String> comunas) {
        return comunas == null || comunas.isEmpty() ? "" : String.join(", ", comunas);
    }

    private String formatearPrecio(Long precio) {
        NumberFormat formato = NumberFormat.getNumberInstance(new Locale("es", "CL"));
        return "$" + formato.format(precio);
    }
}
