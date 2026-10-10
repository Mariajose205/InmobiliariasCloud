package cl.inmobiliarias.service.service.messaging.consumer.pdf;

import cl.inmobiliarias.service.dto.InformeSolicitudMsg;
import cl.inmobiliarias.service.service.InformeService;
import cl.inmobiliarias.service.service.messaging.Reintentos;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Consumidor del dominio PDF.
 *
 * Escucha `cola.pdf.procesamiento` (alimentada por el exchange direct
 * `inmobiliaria.direct` con la routing key `informe.pdf.generar`). Obtiene los
 * datos de la base de datos (todas las propiedades si es ADMIN, solo las
 * propias si es CORREDOR), genera el PDF, lo guarda en disco y lo envia por
 * correo al solicitante.
 *
 * Gestion de confirmacion (ACK manual) y errores:
 *   - Exito total -> basicAck.
 *   - Fallo temporal (BD, generacion, escritura, correo) -> se reintenta con
 *     backoff (Reintentos); agotados, se hace basicNack(requeue=false) y el
 *     DLX mueve el mensaje a `cola.pdf.procesamiento.dlq`.
 *
 * Un informe sin propiedades NO es un error: se genera un PDF que lo indica y
 * se confirma normalmente (evita bucles de reintento inutiles).
 */
@Component
public class PdfConsumer {

    private static final Logger log = LoggerFactory.getLogger(PdfConsumer.class);

    private static final int MAX_INTENTOS = 3;
    private static final long INTERVALO_INICIAL_MS = 2000L;
    private static final double MULTIPLICADOR = 2.0;

    private final InformeService informeService;
    private final JavaMailSender mailSender;
    private final String directorioSalida;

    public PdfConsumer(InformeService informeService,
                       JavaMailSender mailSender,
                       @Value("${informes.directorio:./informes}") String directorioSalida) {
        this.informeService = informeService;
        this.mailSender = mailSender;
        this.directorioSalida = directorioSalida;
    }

    @RabbitListener(queues = "${rabbitmq.queues.pdf-procesamiento}")
    public void consumir(InformeSolicitudMsg mensaje,
                         Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("[PDF] Procesando solicitud de {} (rol={})",
                mensaje.solicitadoPorEmail(), mensaje.rol());
        try {
            Reintentos.conReintentos("PDF", MAX_INTENTOS, INTERVALO_INICIAL_MS, MULTIPLICADOR, log, () -> {
                byte[] pdf = informeService.generarPdf(mensaje);
                Path archivo = guardar(pdf, mensaje);
                enviarPorCorreo(mensaje, pdf, archivo);
            });

            channel.basicAck(deliveryTag, false);
            log.info("[PDF] ACK tag={}. Informe de {} listo.", deliveryTag, mensaje.solicitadoPorEmail());
        } catch (Exception ex) {
            log.error("[PDF] FALLO definitivo (tag={}): NACK a DLQ. Causa: {}",
                    deliveryTag, ex.getMessage());
            nack(deliveryTag, channel);
        }
    }

    private void nack(long deliveryTag, Channel channel) {
        try {
            channel.basicNack(deliveryTag, false, false);
        } catch (IOException ex) {
            log.error("[PDF] No fue posible hacer NACK del tag={}: {}", deliveryTag, ex.getMessage(), ex);
        }
    }

    private Path guardar(byte[] pdf, InformeSolicitudMsg mensaje) throws IOException {
        Path directorio = Path.of(directorioSalida);
        if (!directorio.isAbsolute()) {
            directorio = Path.of(System.getProperty("user.dir")).resolve(directorioSalida);
        }
        Files.createDirectories(directorio);
        String nombre = "informe-" + sanitizar(mensaje.solicitadoPorEmail())
                + "-" + Instant.now().toEpochMilli() + ".pdf";
        Path destino = directorio.resolve(nombre);
        Files.write(destino, pdf);
        return destino;
    }

    private void enviarPorCorreo(InformeSolicitudMsg mensaje, byte[] pdf, Path archivo) {
        try {
            MimeMessage mime = getMailSender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom("no-reply@inmobiliariaduoc.onmicrosoft.com");
            helper.setTo(mensaje.solicitadoPorEmail());
            helper.setSubject("Tu informe de propiedades esta listo");
            helper.setText("Hola,\n\nAdjuntamos el informe de propiedades que solicitaste.\n\n"
                    + "Equipo Inmobiliarias Duoc");
            helper.addAttachment(archivo.getFileName().toString(), () -> new java.io.ByteArrayInputStream(pdf));
            getMailSender().send(mime);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo enviar el informe por correo: " + ex.getMessage(), ex);
        }
    }

    private String sanitizar(String valor) {
        return valor == null ? "anonimo" : valor.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    protected JavaMailSender getMailSender() {
        return mailSender;
    }
}
