package cl.inmobiliarias.service.service;

import cl.inmobiliarias.service.dto.InformeSolicitudMsg;
import cl.inmobiliarias.service.model.Propiedad;
import cl.inmobiliarias.service.repository.PropiedadRepository;
import cl.inmobiliarias.service.security.IdentidadInterceptor;
import cl.inmobiliarias.service.util.InformePdfGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Decide que propiedades entran en el informe y genera el PDF.
 *
 * Regla de negocio diferenciada por rol:
 *   - ADMIN     -> TODAS las propiedades (las creara quien las creara).
 *   - CORREDOR  -> solo las que el mismo creo (se busca por email y, si no hay
 *                  coincidencias, por objectId como respaldo).
 *
 * Los datos se leen directamente de la base de datos del proyecto a traves de
 * {@link PropiedadRepository}, tal como pide el enunciado.
 */
@Service
public class InformeService {

    private static final Logger log = LoggerFactory.getLogger(InformeService.class);

    private final PropiedadRepository repository;

    public InformeService(PropiedadRepository repository) {
        this.repository = repository;
    }

    /**
     * Filtra las propiedades segun el rol del solicitante.
     */
    public List<Propiedad> propiedadesPara(InformeSolicitudMsg msg) {
        if (IdentidadInterceptor.ROL_ADMIN.equals(msg.rol())) {
            List<Propiedad> todas = repository.findAll();
            log.info("[Informe] ADMIN {} -> {} propiedades (todas)", msg.solicitadoPorEmail(), todas.size());
            return todas;
        }

        List<Propiedad> propias = repository.findByCreadoPorEmailIgnoreCase(msg.solicitadoPorEmail());
        if (propias.isEmpty() && msg.solicitadoPorOid() != null && !msg.solicitadoPorOid().isBlank()) {
            propias = repository.findByCreadoPorOidIgnoreCase(msg.solicitadoPorOid());
        }
        log.info("[Informe] CORREDOR {} -> {} propiedades (propias)", msg.solicitadoPorEmail(), propias.size());
        return propias;
    }

    /**
     * Genera el PDF aplicando el filtro por rol.
     */
    public byte[] generarPdf(InformeSolicitudMsg msg) {
        List<Propiedad> propiedades = propiedadesPara(msg);
        String titulo = IdentidadInterceptor.ROL_ADMIN.equals(msg.rol())
                ? "Informe de propiedades (todas)"
                : "Informe de mis propiedades";
        return InformePdfGenerator.generar(titulo, msg.solicitadoPorEmail(), propiedades);
    }
}
