package cl.inmobiliarias.wishlist.controller;

import cl.inmobiliarias.wishlist.dto.AvisoResponse;
import cl.inmobiliarias.wishlist.security.IdentidadInterceptor;
import cl.inmobiliarias.wishlist.security.SeguridadProperties;
import cl.inmobiliarias.wishlist.service.AvisoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Avisos de propiedades eliminadas que ve el cliente en su pagina.
 *
 * GET /api/cliente/avisos -> "La propiedad X ya fue vendida y se elimino de
 *                            tus favoritos"
 * DELETE /api/cliente/avisos/{id} -> el usuario descarta el aviso
 *
 * Se expone tambien el alias /api/wishlist/avisos para las pruebas del staff.
 * Los avisos quedan aislados por el email que inyecta API Gateway.
 */
@RestController
@RequestMapping({"/api/wishlist/avisos", "/api/cliente/avisos"})
public class AvisosController {

    private final AvisoService service;
    private final SeguridadProperties seguridad;

    public AvisosController(AvisoService service, SeguridadProperties seguridad) {
        this.service = service;
        this.seguridad = seguridad;
    }

    @GetMapping
    public List<AvisoResponse> listar(
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario) {
        return service.listar(resolverEmail(usuario));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> descartar(
            @PathVariable Long id,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario) {
        service.descartar(resolverEmail(usuario), id);
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
