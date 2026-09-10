package cl.inmobiliarias.service.controller;

import cl.inmobiliarias.service.model.Propiedad;
import cl.inmobiliarias.service.service.AuditoriaService;
import cl.inmobiliarias.service.service.PropiedadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/propiedades")
public class PropiedadController {

    private final PropiedadService service;
    private final AuditoriaService auditoriaService;

    public PropiedadController(PropiedadService service, AuditoriaService auditoriaService) {
        this.service = service;
        this.auditoriaService = auditoriaService;
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
            @RequestHeader(value = "X-User", defaultValue = "") String usuario,
            @RequestHeader(value = "X-User-Rol", defaultValue = "") String rol) {
        Propiedad creada = service.crear(propiedad);
        auditoriaService.registrar("CREAR", creada.getTitulo(), usuario, rol);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}")
    public Propiedad actualizar(
            @PathVariable Long id,
            @Valid @RequestBody Propiedad propiedad,
            @RequestHeader(value = "X-User", defaultValue = "") String usuario,
            @RequestHeader(value = "X-User-Rol", defaultValue = "") String rol) {
        Propiedad actualizada = service.actualizar(id, propiedad);
        auditoriaService.registrar("EDITAR", actualizada.getTitulo(), usuario, rol);
        return actualizada;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long id,
            @RequestHeader(value = "X-User", defaultValue = "") String usuario,
            @RequestHeader(value = "X-User-Rol", defaultValue = "") String rol) {
        Propiedad propiedad = service.obtener(id);
        auditoriaService.registrar("ELIMINAR", propiedad.getTitulo(), usuario, rol);
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
