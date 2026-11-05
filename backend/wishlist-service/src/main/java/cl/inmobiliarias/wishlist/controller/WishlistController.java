package cl.inmobiliarias.wishlist.controller;

import cl.inmobiliarias.wishlist.dto.FavoritoRequest;
import cl.inmobiliarias.wishlist.model.Favorito;
import cl.inmobiliarias.wishlist.security.IdentidadInterceptor;
import cl.inmobiliarias.wishlist.security.SeguridadProperties;
import cl.inmobiliarias.wishlist.service.WishlistService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD de la wishlist (favoritos) del usuario autenticado.
 *
 * La identidad llega en atributos de la peticion que deja
 * {@link IdentidadInterceptor}. En desarrollo local, sin API Gateway delante,
 * se usa el email "usuario@local" para poder probar sin token.
 */
@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private static final Logger log = LoggerFactory.getLogger(WishlistController.class);

    private final WishlistService service;
    private final SeguridadProperties seguridad;

    public WishlistController(WishlistService service, SeguridadProperties seguridad) {
        this.service = service;
        this.seguridad = seguridad;
    }

    @GetMapping("/favoritos")
    public List<Favorito> listarFavoritos(
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario) {
        return service.listarFavoritos(resolverEmail(usuario));
    }

    @PostMapping("/favoritos")
    public ResponseEntity<Favorito> agregarFavorito(
            @Valid @RequestBody FavoritoRequest request,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_OID, required = false) String oid,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_ROL, required = false) String rol) {
        String email = resolverEmail(usuario);
        log.info("[Wishlist] Agregar favorito propiedad={} usuario={} rol={}", request.getPropiedadId(), email, rol);
        Favorito guardado = service.agregarFavorito(email, oid, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
    }

    @DeleteMapping("/favoritos/{propiedadId}")
    public ResponseEntity<Void> eliminarFavorito(
            @PathVariable Long propiedadId,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario) {
        service.eliminarFavorito(resolverEmail(usuario), propiedadId);
        return ResponseEntity.noContent().build();
    }

    /**
     * En produccion el interceptor ya bloqueo la peticion sin identidad; esta
     * es solo una defensa extra. En desarrollo se usa "usuario@local".
     */
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
