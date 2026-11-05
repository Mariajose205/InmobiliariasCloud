package cl.inmobiliarias.wishlist.controller.admin;

import cl.inmobiliarias.wishlist.dto.admin.ConsumerGroupInfoResponse;
import cl.inmobiliarias.wishlist.dto.admin.TopicInfoResponse;
import cl.inmobiliarias.wishlist.dto.admin.TopicRequest;
import cl.inmobiliarias.wishlist.service.messaging.KafkaAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administracion de Kafka (solo ADMIN, lo aplica IdentidadInterceptor).
 */
@RestController
@RequestMapping("/api/admin/kafka")
public class KafkaAdminController {

    private final KafkaAdminService service;

    public KafkaAdminController(KafkaAdminService service) {
        this.service = service;
    }

    @GetMapping("/topicos")
    public List<String> listarTopicos() {
        return service.listarTopicos();
    }

    @GetMapping("/topicos/{nombre}")
    public TopicInfoResponse describirTopico(@PathVariable String nombre) {
        return service.describirTopico(nombre);
    }

    @PostMapping("/topicos")
    public ResponseEntity<TopicInfoResponse> crearTopico(@Valid @RequestBody TopicRequest request) {
        TopicInfoResponse creada = service.crearTopico(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @DeleteMapping("/topicos/{nombre}")
    public ResponseEntity<Void> eliminarTopico(@PathVariable String nombre) {
        service.eliminarTopico(nombre);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/grupos")
    public List<ConsumerGroupInfoResponse> listarGrupos() {
        return service.listarGrupos();
    }

    @GetMapping("/grupos/{groupId}")
    public ConsumerGroupInfoResponse describirGrupo(@PathVariable String groupId) {
        return service.describirGrupo(groupId);
    }
}
