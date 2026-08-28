package cl.inmobiliarias.service.controller;

import cl.inmobiliarias.service.model.Propiedad;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/propiedades")
public class PropiedadController {

    private final PropiedadService service;

    public PropiedadController(PropiedadService service) {
        this.service = service;
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
    public ResponseEntity<Propiedad> crear(@Valid @RequestBody Propiedad propiedad) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(propiedad));
    }

    @PutMapping("/{id}")
    public Propiedad actualizar(@PathVariable Long id, @Valid @RequestBody Propiedad propiedad) {
        return service.actualizar(id, propiedad);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
