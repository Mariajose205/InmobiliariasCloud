package cl.inmobiliarias.service.service;

import cl.inmobiliarias.service.model.Auditoria;
import cl.inmobiliarias.service.repository.AuditoriaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditoriaService {

    private final AuditoriaRepository repository;

    public AuditoriaService(AuditoriaRepository repository) {
        this.repository = repository;
    }

    /**
     * Registra una accion de auditoria. El usuario es el email (o identificador)
     * de quien realizo la accion, enviado por el API Gateway en los headers.
     */
    public void registrar(String accion, String propiedad, String usuario, String rol) {
        String identidad = (usuario == null || usuario.isBlank()) ? "desconocido" : usuario;
        repository.save(new Auditoria(accion, propiedad, identidad, rol));
    }

    public List<Auditoria> listar() {
        return repository.findAllByOrderByFechaDesc();
    }
}