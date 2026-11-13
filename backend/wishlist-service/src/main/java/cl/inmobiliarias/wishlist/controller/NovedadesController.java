package cl.inmobiliarias.wishlist.controller;

import cl.inmobiliarias.wishlist.dto.ResumenNovedadesResponse;
import cl.inmobiliarias.wishlist.security.IdentidadInterceptor;
import cl.inmobiliarias.wishlist.security.SeguridadProperties;
import cl.inmobiliarias.wishlist.service.ResumenNovedadesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Novedades automaticas visibles en la pagina principal.
 *
 * Ya no se muestra una entrada por propiedad: el backend agrupa las novedades
 * en resumenes (cada 6 propiedades o en el barrido diario) y este endpoint
 * devuelve UNA entrada por resumen con el detalle de sus propiedades.
 *
 * GET /api/wishlist/novedades -> lista de resumenes (polling cada ~15s)
 * DELETE /api/wishlist/novedades/{id} -> descarta el resumen y sus novedades
 *
 * Los clientes de Cognito usan el alias /api/cliente/novedades.
 */
@RestController
@RequestMapping({"/api/wishlist/novedades", "/api/cliente/novedades"})
public class NovedadesController {

    private final ResumenNovedadesService service;
    private final SeguridadProperties seguridad;

    public NovedadesController(ResumenNovedadesService service, SeguridadProperties seguridad) {
        this.service = service;
        this.seguridad = seguridad;
    }

    @GetMapping
    public List<ResumenNovedadesResponse> listar(
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario) {
        return service.listarResumenes(resolverEmail(usuario));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> descartar(
            @PathVariable Long id,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario) {
        service.descartarResumen(resolverEmail(usuario), id);
        return ResponseEntity.noContent().build();
    }

    private String resolverEmail(String usuario) {
        if (usuario == null || usuario.isBlank() || "desconocido".equals(usuario)) {
            if (seguridad.isAutorizacionRequerida()) {
                throw new RuntimeException("Acceso denegado: la peticion no fue validada por el API Gateway.");
            }
            return "usuario@local";
        }
        return usuario;
    }
}
