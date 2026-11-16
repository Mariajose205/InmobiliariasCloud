package cl.inmobiliarias.wishlist.service.messaging;

import cl.inmobiliarias.wishlist.dto.PropiedadEliminadaEvent;
import cl.inmobiliarias.wishlist.service.AvisoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Consumidor del topic {@code propiedades-eliminadas}.
 *
 * Cuando un administrador o corredor borra una propiedad, este listener:
 *   - la quita de los favoritos de todos los usuarios, y
 *   - deja un aviso personal en la pagina de cada uno ("ya fue vendida o
 *     arrendada, se elimino de tus favoritos").
 *
 * Usa un grupo de consumidores propio para no compartir offsets con el flujo de
 * propiedades nuevas. ACK manual: si falla, el DefaultErrorHandler reintenta y
 * al agotar publica en {@code propiedades-eliminadas.dlq}.
 */
@Service
public class PropiedadEliminadaNotificador {

    private static final Logger log = LoggerFactory.getLogger(PropiedadEliminadaNotificador.class);

    private final AvisoService avisoService;

    public PropiedadEliminadaNotificador(AvisoService avisoService) {
        this.avisoService = avisoService;
    }

    @KafkaListener(topics = "${app.kafka.topics.propiedades-eliminadas}",
            groupId = "wishlist-bajas-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void recibir(PropiedadEliminadaEvent evento, Acknowledgment ack) {
        if (evento == null || evento.getPropiedadId() == null) {
            log.warn("Evento de propiedad eliminada invalido, se descarta: {}", evento);
            if (ack != null) {
                ack.acknowledge();
            }
            return;
        }

        log.info("Recibida propiedad eliminada id={} titulo='{}' tipoOperacion={}",
                evento.getPropiedadId(), evento.getTitulo(), evento.getTipoOperacion());

        avisoService.registrarEliminacion(evento);

        if (ack != null) {
            ack.acknowledge();
        }
    }
}
