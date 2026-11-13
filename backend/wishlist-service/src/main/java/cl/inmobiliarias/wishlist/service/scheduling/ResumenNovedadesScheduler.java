package cl.inmobiliarias.wishlist.service.scheduling;

import cl.inmobiliarias.wishlist.service.ResumenNovedadesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barrido diario del resumen de novedades.
 *
 * Aunque el resumen se dispara al juntar 6 propiedades, un usuario con 1..5
 * pendientes quedaria sin aviso hasta que se junten mas. Este job envia, una
 * vez al dia, el resumen de todo lo que quedo pendiente. El CRON y la zona se
 * configuran en app.notificaciones (por defecto 20:00 de America/Santiago).
 */
@Component
public class ResumenNovedadesScheduler {

    private static final Logger log = LoggerFactory.getLogger(ResumenNovedadesScheduler.class);

    private final ResumenNovedadesService service;

    public ResumenNovedadesScheduler(ResumenNovedadesService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.notificaciones.cron-resumen:0 0 20 * * *}",
            zone = "${app.notificaciones.zona:America/Santiago}")
    public void enviarResumenesPendientes() {
        try {
            int creados = service.generarResumenesPendientes();
            log.info("Barrido diario de novedades completado: {} resumen(es) generados", creados);
        } catch (RuntimeException ex) {
            // Una excepcion no debe matar el scheduler: se registra y el
            // proximo dia se reintenta. Los pendientes siguen en la BD.
            log.error("Error en el barrido diario de novedades: {}", ex.getMessage(), ex);
        }
    }
}
