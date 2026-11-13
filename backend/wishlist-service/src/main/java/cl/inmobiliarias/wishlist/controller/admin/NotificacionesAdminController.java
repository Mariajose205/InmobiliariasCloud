package cl.inmobiliarias.wishlist.controller.admin;

import cl.inmobiliarias.wishlist.service.ResumenNovedadesService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Administracion del resumen de novedades (solo ADMIN: lo aplica
 * IdentidadInterceptor sobre /api/admin/**).
 *
 * Permite forzar el barrido diario sin esperar al CRON. Es util para probar
 * el flujo o para enviar los pendientes antes de una demo.
 */
@RestController
@RequestMapping("/api/admin/notificaciones")
public class NotificacionesAdminController {

    private final ResumenNovedadesService service;

    public NotificacionesAdminController(ResumenNovedadesService service) {
        this.service = service;
    }

    @PostMapping("/resumen")
    public Map<String, Object> ejecutarResumenPendientes() {
        int generados = service.generarResumenesPendientes();
        return Map.of(
                "resumenesGenerados", generados,
                "mensaje", generados == 0
                        ? "No habia novedades pendientes"
                        : "Resumen(es) publicados en notificaciones-sistema");
    }
}
