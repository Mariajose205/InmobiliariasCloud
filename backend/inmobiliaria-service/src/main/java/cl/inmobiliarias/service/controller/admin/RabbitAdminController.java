package cl.inmobiliarias.service.controller.admin;

import cl.inmobiliarias.service.dto.admin.BindingRequest;
import cl.inmobiliarias.service.dto.admin.ExchangeRequest;
import cl.inmobiliarias.service.dto.admin.QueueRequest;
import cl.inmobiliarias.service.service.messaging.admin.RabbitAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * API de administracion del broker de mensajeria. Todo el prefijo /api/admin
 * queda restringido a rol ADMIN por IdentidadInterceptor.
 *
 * Endpoints:
 *   GET    /api/admin/rabbitmq/queues/{name}          -> estado de la cola
 *   POST   /api/admin/rabbitmq/queues                 -> crea la cola
 *   DELETE /api/admin/rabbitmq/queues/{name}          -> elimina la cola
 *   POST   /api/admin/rabbitmq/queues/{name}/purge    -> vacia la cola
 *   POST   /api/admin/rabbitmq/exchanges              -> crea el exchange
 *   DELETE /api/admin/rabbitmq/exchanges/{name}       -> elimina el exchange
 *   POST   /api/admin/rabbitmq/bindings               -> crea el binding
 */
@RestController
@RequestMapping("/api/admin/rabbitmq")
public class RabbitAdminController {

    private final RabbitAdminService servicio;

    public RabbitAdminController(RabbitAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/queues/{name}")
    public Map<String, Object> estadoCola(@PathVariable String name) {
        return servicio.infoCola(name);
    }

    @PostMapping("/queues")
    public ResponseEntity<Map<String, Object>> crearCola(@RequestBody QueueRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre de la cola es obligatorio."));
        }
        servicio.declararCola(req.name(), req.durableOrDefault(), req.exclusiveOrDefault(),
                req.autoDeleteOrDefault(), req.deadLetterExchange(), req.deadLetterRoutingKey());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "creada", "cola", req.name()));
    }

    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Map<String, Object>> eliminarCola(
            @PathVariable String name,
            @RequestParam(defaultValue = "true") boolean ifUnused,
            @RequestParam(defaultValue = "true") boolean ifEmpty) {
        servicio.eliminarCola(name, ifUnused, ifEmpty);
        return ResponseEntity.ok(Map.of("status", "eliminada", "cola", name));
    }

    @PostMapping("/queues/{name}/purge")
    public ResponseEntity<Map<String, Object>> purgarCola(@PathVariable String name) {
        servicio.purgarCola(name);
        return ResponseEntity.ok(Map.of("status", "purgada", "cola", name));
    }

    @PostMapping("/exchanges")
    public ResponseEntity<Map<String, Object>> crearExchange(@RequestBody ExchangeRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre del exchange es obligatorio."));
        }
        servicio.declararExchange(req.name(), req.typeOrDefault(), req.durableOrDefault(),
                req.autoDeleteOrDefault());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "creado", "exchange", req.name(), "tipo", req.typeOrDefault()));
    }

    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Map<String, Object>> eliminarExchange(@PathVariable String name) {
        servicio.eliminarExchange(name);
        return ResponseEntity.ok(Map.of("status", "eliminado", "exchange", name));
    }

    @PostMapping("/bindings")
    public ResponseEntity<Map<String, Object>> crearBinding(@RequestBody BindingRequest req) {
        servicio.bind(req.queue(), req.exchange(), req.routingKey(), req.exchangeTypeOrDefault());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "vinculado", "cola", req.queue(), "exchange", req.exchange()));
    }
}

