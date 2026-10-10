package cl.inmobiliarias.service.service.messaging;

import cl.inmobiliarias.service.config.rabbitmq.RabbitProperties;
import cl.inmobiliarias.service.dto.InformeSolicitudMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Publica la solicitud de informe en los dos caminos del flujo. Los nombres de
 * exchange y routing key se toman de {@link RabbitProperties} (application.yml).
 *
 *   1) inmobiliaria.fanout  (sin routing key)  -> notificacion al usuario
 *   2) inmobiliaria.direct  con la routing key -> generacion del PDF
 */
@Service
public class InformePublisher {

    private static final Logger log = LoggerFactory.getLogger(InformePublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties propiedades;

    public InformePublisher(RabbitTemplate rabbitTemplate, RabbitProperties propiedades) {
        this.rabbitTemplate = rabbitTemplate;
        this.propiedades = propiedades;
    }

    public void publicarSolicitud(InformeSolicitudMsg mensaje) {
        String fanout = propiedades.getExchanges().getFanout();
        String direct = propiedades.getExchanges().getDirect();
        String routingPdf = propiedades.getRoutingKeys().getPdf();

        // Camino 1: aviso al usuario (fanout -> cola.correos.notificaciones).
        rabbitTemplate.convertAndSend(fanout, "", mensaje);
        log.info("[Publisher] Solicitud enviada a {} para {}", fanout, mensaje.solicitadoPorEmail());

        // Camino 2: trabajo de generacion (direct -> cola.pdf.procesamiento).
        rabbitTemplate.convertAndSend(direct, routingPdf, mensaje);
        log.info("[Publisher] Tarea enviada a {} (rk={}) para {}",
                direct, routingPdf, mensaje.solicitadoPorEmail());
    }
}
