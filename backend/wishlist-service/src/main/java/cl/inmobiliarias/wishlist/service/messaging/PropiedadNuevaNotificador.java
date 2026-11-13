package cl.inmobiliarias.wishlist.service.messaging;

import cl.inmobiliarias.wishlist.dto.PropiedadCreadaEvent;
import cl.inmobiliarias.wishlist.model.AlertaComuna;
import cl.inmobiliarias.wishlist.model.Novedad;
import cl.inmobiliarias.wishlist.repository.AlertaComunaRepository;
import cl.inmobiliarias.wishlist.repository.NovedadRepository;
import cl.inmobiliarias.wishlist.service.ResumenNovedadesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Consumidor del topic propiedades-nuevas.
 *
 * Por cada propiedad nueva busca las alertas de esa comuna y deja una Novedad
 * PENDIENTE por usuario (aun no se envia correo). Despues intenta crear el
 * resumen: si el usuario ya acumulo el umbral (6 por defecto), se publica UN
 * solo correo con todas las propiedades en vez de uno por propiedad. Lo que no
 * alcanza el umbral se envia en el barrido diario (ResumenNovedadesScheduler).
 *
 * La pagina principal tambien ve las propiedades agrupadas por resumen.
 */
@Service
public class PropiedadNuevaNotificador {

    private static final Logger log = LoggerFactory.getLogger(PropiedadNuevaNotificador.class);

    private final AlertaComunaRepository alertaComunaRepository;
    private final NovedadRepository novedadRepository;
    private final ResumenNovedadesService resumenService;

    public PropiedadNuevaNotificador(AlertaComunaRepository alertaComunaRepository,
                                     NovedadRepository novedadRepository,
                                     ResumenNovedadesService resumenService) {
        this.alertaComunaRepository = alertaComunaRepository;
        this.novedadRepository = novedadRepository;
        this.resumenService = resumenService;
    }

    @KafkaListener(topics = "${app.kafka.topics.propiedades-nuevas}",
            groupId = "wishlist-notifier-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void recibir(PropiedadCreadaEvent evento,
                        @Header(KafkaHeaders.RECEIVED_KEY) String comunaClave,
                        Acknowledgment ack, Message<?> message) {
        // Validacion defensiva: un evento incompleto se descarta sin reintento.
        if (evento == null || evento.getPropiedadId() == null
                || evento.getTitulo() == null || evento.getTitulo().isBlank()) {
            log.warn("Evento de propiedad nueva invalido recibido (clave={}), se descarta: {}", comunaClave, evento);
            if (ack != null) {
                ack.acknowledge();
            }
            return;
        }

        log.info("Recibida propiedad nueva id={} titulo='{}' clave={} comuna={}",
                evento.getPropiedadId(), evento.getTitulo(), comunaClave, evento.getComuna());

        // La comuna viene en la clave; si falta, se usa la del evento.
        String comuna = comunaClave != null && !comunaClave.isBlank()
                ? comunaClave : evento.getComuna();
        if (comuna == null || comuna.isBlank()) {
            log.warn("Propiedad id={} sin comuna, no se puede avisar a ningun usuario", evento.getPropiedadId());
            if (ack != null) {
                ack.acknowledge();
            }
            return;
        }

        List<AlertaComuna> alertas = alertaComunaRepository.findByComunaIgnoreCase(comuna);
        if (alertas.isEmpty()) {
            log.info("Nadie sigue la comuna {} (propiedad id={}), sin notificaciones",
                    comuna, evento.getPropiedadId());
        }
        for (AlertaComuna alerta : alertas) {
            String mensaje = "Nueva propiedad en " + comuna + ": " + evento.getTitulo();
            // 1) Novedad PENDIENTE (visible en Home agrupada al formarse el resumen).
            try {
                novedadRepository.save(new Novedad(
                        alerta.getUsuarioEmail(), alerta.getUsuarioOid(), comuna,
                        evento.getPropiedadId(), evento.getTitulo(),
                        evento.getPrecio(), evento.getTipoPrecio(),
                        evento.getUbicacion(), mensaje));
            } catch (RuntimeException ex) {
                // La restriccion unica (usuario, propiedad) evita duplicados si
                // el evento se reintenta: se ignora y se sigue con el resumen.
                log.warn("No se pudo guardar novedad para {} propiedad={}: {}",
                        alerta.getUsuarioEmail(), evento.getPropiedadId(), ex.getMessage());
            }
            // 2) Si el usuario llego al umbral, se crea UN resumen y se publica
            //    UN solo correo con las propiedades acumuladas.
            resumenService.crearSiAlcanzaUmbral(alerta.getUsuarioEmail(), alerta.getUsuarioOid());
        }

        if (ack != null) {
            ack.acknowledge();
        }
    }
}
