package cl.inmobiliarias.service.service;

import cl.inmobiliarias.service.model.Propiedad;
import cl.inmobiliarias.service.repository.PropiedadRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PropiedadService {

    private final PropiedadRepository repository;

    public PropiedadService(PropiedadRepository repository) {
        this.repository = repository;
    }

    public List<Propiedad> listar() {
        return repository.findAll();
    }

    public List<Propiedad> listarPorCategoria(String categoria) {
        return repository.findByCategoria(categoria);
    }

    public Propiedad obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propiedad no encontrada con id: " + id));
    }

    public Propiedad crear(Propiedad propiedad) {
        propiedad.setId(null);
        return repository.save(propiedad);
    }

    public Propiedad actualizar(Long id, Propiedad datos) {
        Propiedad existente = obtener(id);
        existente.setTitulo(datos.getTitulo());
        existente.setCategoria(datos.getCategoria());
        existente.setTipoOperacion(datos.getTipoOperacion());
        existente.setPrecio(datos.getPrecio());
        existente.setTipoPrecio(datos.getTipoPrecio());
        existente.setGastosComunes(datos.getGastosComunes());
        existente.setUbicacion(datos.getUbicacion());
        existente.setPiezas(datos.getPiezas());
        existente.setBanos(datos.getBanos());
        existente.setEstacionamiento(datos.getEstacionamiento());
        existente.setBodega(datos.getBodega());
        existente.setVideoUrl(datos.getVideoUrl());
        existente.setImagenes(datos.getImagenes());
        return repository.save(existente);
    }

    public void eliminar(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Propiedad no encontrada con id: " + id);
        }
        repository.deleteById(id);
    }
}
