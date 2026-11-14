package cl.inmobiliarias.wishlist.controller.admin;

import cl.inmobiliarias.wishlist.service.EstadisticasService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Metricas del lado del cliente para el dashboard del administrador.
 *
 * Va bajo /api/admin/** a proposito: IdentidadInterceptor exige el rol ADMIN
 * (que llega en la cabecera X-User-Rol que escribe API Gateway desde el token
 * de Entra ID), asi que un CORREDOR o un cliente de Cognito recibe 403.
 *
 * GET /api/admin/estadisticas
 */
@RestController
@RequestMapping("/api/admin/estadisticas")
public class EstadisticasAdminController {

    private final EstadisticasService service;

    public EstadisticasAdminController(EstadisticasService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> resumen() {
        return service.resumen();
    }
}
