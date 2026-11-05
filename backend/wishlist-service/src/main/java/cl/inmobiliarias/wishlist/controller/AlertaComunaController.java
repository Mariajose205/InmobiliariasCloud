package cl.inmobiliarias.wishlist.controller;

import cl.inmobiliarias.wishlist.dto.AlertaRequest;
import cl.inmobiliarias.wishlist.model.AlertaComuna;
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
 * Alertas por comuna del usuario autenticado.
 *
 * Las comunas disponibles se exponen en ComunaController (/api/wishlist/comunas).
 */
@RestController
@RequestMapping("/api/wishlist/alertas")
public class AlertaComunaController {

    private static final Logger log = LoggerFactory.getLogger(AlertaComunaController.class);

    private final WishlistService service;
    private final SeguridadProperties seguridad;

    public AlertaComunaController(WishlistService service, SeguridadProperties seguridad) {
        this.service = service;
        this.seguridad = seguridad;
    }

    @GetMapping
    public List<AlertaComuna> listar(
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario) {
        return service.listarAlertas(resolverEmail(usuario));
    }

    @PostMapping
    public ResponseEntity<AlertaComuna> seguir(
            @Valid @RequestBody AlertaRequest request,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_OID, required = false) String oid) {
        String email = resolverEmail(usuario);
        log.info("[Wishlist] Seguir comuna={} usuario={}", request.getComuna(), email);
        AlertaComuna alerta = service.seguirComuna(email, oid, request.getComuna());
        return ResponseEntity.status(HttpStatus.CREATED).body(alerta);
    }

    @DeleteMapping("/{comuna}")
    public ResponseEntity<Void> dejarDeSeguir(
            @PathVariable String comuna,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario) {
        service.dejarDeSeguirComuna(resolverEmail(usuario), comuna);
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
