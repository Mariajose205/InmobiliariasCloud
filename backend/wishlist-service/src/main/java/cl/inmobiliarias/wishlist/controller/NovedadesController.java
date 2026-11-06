package cl.inmobiliarias.wishlist.controller;

import cl.inmobiliarias.wishlist.model.Novedad;
import cl.inmobiliarias.wishlist.security.IdentidadInterceptor;
import cl.inmobiliarias.wishlist.security.SeguridadProperties;
import cl.inmobiliarias.wishlist.service.WishlistService;
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
 * Se crean solas cuando se publica una propiedad nueva en una comuna
 * que el usuario sigue (PropiedadNuevaNotificador). No hay paso manual:
 * el usuario solo las ve y puede descartarlas.
 *
 * GET /api/wishlist/novedades -> lista para Home (polling cada ~15s)
 * DELETE /api/wishlist/novedades/{id} -> descarta una novedad
 */
@RestController
@RequestMapping("/api/wishlist/novedades")
public class NovedadesController {

    private final WishlistService service;
    private final SeguridadProperties seguridad;

    public NovedadesController(WishlistService service, SeguridadProperties seguridad) {
        this.service = service;
        this.seguridad = seguridad;
    }

    @GetMapping
    public List<Novedad> listar(
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario) {
        return service.listarNovedades(resolverEmail(usuario));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> descartar(
            @PathVariable Long id,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario) {
        service.eliminarNovedad(resolverEmail(usuario), id);
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
