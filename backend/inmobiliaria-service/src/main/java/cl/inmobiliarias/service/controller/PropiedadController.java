package cl.inmobiliarias.service.controller;

import cl.inmobiliarias.service.model.Propiedad;
import cl.inmobiliarias.service.security.IdentidadInterceptor;
import cl.inmobiliarias.service.service.AuditoriaService;
import cl.inmobiliarias.service.service.PropiedadService;
import cl.inmobiliarias.service.service.messaging.AuditoriaEventosPublisher;
import cl.inmobiliarias.service.service.messaging.PropiedadNuevaPublisher;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD del catalogo de propiedades.
 *
 * La identidad del autor de cada cambio ya no llega en las cabeceras
 * `X-User` / `X-User-Rol` que escribia el gateway antiguo, sino en los
 * atributos de la peticion que deja {@link IdentidadInterceptor} tras resolver el
 * rol a partir de la identidad validada por API Gateway.
 *
 * El control de acceso por rol tambien lo aplica ese interceptor, asi que los
 * metodos de escritura asumen que ya pasaron la validacion.
 */
@RestController
@RequestMapping("/api/propiedades")
public class PropiedadController {

    private final PropiedadService service;
    private final AuditoriaService auditoriaService;
    private final PropiedadNuevaPublisher propiedadNuevaPublisher;
    private final AuditoriaEventosPublisher auditoriaEventosPublisher;

    public PropiedadController(PropiedadService service, AuditoriaService auditoriaService,
                               PropiedadNuevaPublisher propiedadNuevaPublisher,
                               AuditoriaEventosPublisher auditoriaEventosPublisher) {
        this.service = service;
        this.auditoriaService = auditoriaService;
        this.propiedadNuevaPublisher = propiedadNuevaPublisher;
        this.auditoriaEventosPublisher = auditoriaEventosPublisher;
    }

    @GetMapping
    public List<Propiedad> listar(@RequestParam(required = false) String categoria) {
        if (categoria != null && !categoria.isBlank()) {
            return service.listarPorCategoria(categoria);
        }
        return service.listar();
    }

    @GetMapping("/{id}")
    public Propiedad obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<Propiedad> crear(
            @Valid @RequestBody Propiedad propiedad,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_NOMBRE, required = false) String nombre,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_OID, required = false) String oid,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_ROL, required = false) String rol) {
        propiedad.setCreadoPorEmail(valor(usuario));
        propiedad.setCreadoPorNombre(valor(nombre));
        propiedad.setCreadoPorOid(valor(oid));
        Propiedad creada = service.crear(propiedad);
        auditoriaService.registrar("CREAR", creada.getTitulo(), valor(usuario), valor(rol));
        // Evento de auditoria por el exchange topic (rk propiedades.eventos.creada).
        // Igual que Kafka: si RabbitMQ falla, el catalogo ya quedo guardado.
        try {
            auditoriaEventosPublisher.publicar(creada, AuditoriaEventosPublisher.ACCION_CREAR,
                    valor(usuario), valor(rol));
        } catch (RuntimeException ex) {
            // Defensa extra: nunca debe romper la creacion de la propiedad.
        }
        // Aviso automatico a usuarios con favoritos en la misma comuna.
        // El wishlist-service crea la novedad en la pagina principal y el correo.
        // Si Kafka falla, el catalogo ya quedo guardado: no se bloquea el POST.
        try {
            propiedadNuevaPublisher.publicar(creada);
        } catch (RuntimeException ex) {
            // Defensa extra: el publicador ya es asincrono, pero nunca debe
            // romper la creacion de la propiedad.
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Propiedad> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody Propiedad propiedad,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_ROL, required = false) String rol) {
        Propiedad actualizada = service.actualizar(id, propiedad);
        auditoriaService.registrar("EDITAR", actualizada.getTitulo(), valor(usuario), valor(rol));
        // Evento de auditoria por el exchange topic (rk propiedades.eventos.editar).
        try {
            auditoriaEventosPublisher.publicar(actualizada, AuditoriaEventosPublisher.ACCION_EDITAR,
                    valor(usuario), valor(rol));
        } catch (RuntimeException ex) {
            // El PUT ya tuvo exito; la bitacora asincrona no debe romperlo.
        }
        return ResponseEntity.ok(actualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long id,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_ROL, required = false) String rol) {
        Propiedad propiedad = service.obtener(id);
        auditoriaService.registrar("ELIMINAR", propiedad.getTitulo(), valor(usuario), valor(rol));
        // Evento de auditoria por el exchange topic (rk propiedades.eventos.eliminar):
        // se publica ANTES de eliminar, porque despues la propiedad ya no existe.
        try {
            auditoriaEventosPublisher.publicar(propiedad, AuditoriaEventosPublisher.ACCION_ELIMINAR,
                    valor(usuario), valor(rol));
        } catch (RuntimeException ex) {
            // El DELETE ya esta autorizado; la bitacora asincrona no debe romperlo.
        }
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    private String valor(String atributo) {
        return atributo == null ? "" : atributo;
    }
}
