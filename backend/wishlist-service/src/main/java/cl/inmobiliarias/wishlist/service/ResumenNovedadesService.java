package cl.inmobiliarias.wishlist.service;

import cl.inmobiliarias.wishlist.config.NotificacionesProperties;
import cl.inmobiliarias.wishlist.dto.NotificacionItem;
import cl.inmobiliarias.wishlist.dto.NotificacionSistemaEvent;
import cl.inmobiliarias.wishlist.dto.ResumenNovedadesResponse;
import cl.inmobiliarias.wishlist.model.Novedad;
import cl.inmobiliarias.wishlist.model.ResumenNovedades;
import cl.inmobiliarias.wishlist.repository.NovedadRepository;
import cl.inmobiliarias.wishlist.repository.ResumenNovedadesRepository;
import cl.inmobiliarias.wishlist.service.messaging.NotificacionKafkaPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Agrupa las novedades por usuario para no saturarlo con correos.
 *
 * Flujo:
 *  1. Cada propiedad nueva en una comuna seguida deja una fila pendiente en
 *     wishlist_novedades (resumenId = null). NO se envia correo en ese momento.
 *  2. Cuando el usuario acumula `app.notificaciones.umbral` pendientes (6 por
 *     defecto), se crea un ResumenNovedades con esas propiedades y se publica
 *     UN solo evento en notificaciones-sistema (un correo con el detalle).
 *  3. Una vez al dia (cron configurable), el scheduler agrupa lo que haya
 *     quedado pendiente (1..5 propiedades) para que nadie quede sin aviso.
 *
 * Los metodos son synchronized porque el listener de Kafka y el scheduler son
 * hilos distintos: en una sola instancia basta con serializar la creacion de
 * resumenes para que un mismo pendiente no entre en dos resumenes.
 */
@Service
public class ResumenNovedadesService {

    private static final Logger log = LoggerFactory.getLogger(ResumenNovedadesService.class);

    private final NovedadRepository novedadRepository;
    private final ResumenNovedadesRepository resumenRepository;
    private final NotificacionKafkaPublisher publisher;
    private final NotificacionesProperties propiedades;

    public ResumenNovedadesService(NovedadRepository novedadRepository,
                                   ResumenNovedadesRepository resumenRepository,
                                   NotificacionKafkaPublisher publisher,
                                   NotificacionesProperties propiedades) {
        this.novedadRepository = novedadRepository;
        this.resumenRepository = resumenRepository;
        this.publisher = publisher;
        this.propiedades = propiedades;
    }

    /**
     * Crea el resumen inmediato si el usuario alcanzo el umbral de pendientes.
     *
     * Toma solo las mas antiguas (hasta el umbral) para que cada correo tenga
     * un tamano acotado; el resto queda pendiente para el siguiente resumen.
     */
    @Transactional
    public synchronized Optional<ResumenNovedades> crearSiAlcanzaUmbral(String usuarioEmail, String usuarioOid) {
        if (usuarioEmail == null || usuarioEmail.isBlank()) {
            return Optional.empty();
        }
        long pendientes = novedadRepository.countByUsuarioEmailIgnoreCaseAndResumenIdIsNull(usuarioEmail);
        if (pendientes < propiedades.getUmbral()) {
            return Optional.empty();
        }
        List<Novedad> items = novedadRepository
                .findByUsuarioEmailIgnoreCaseAndResumenIdIsNullOrderByFechaCreacionAsc(
                        usuarioEmail, PageRequest.of(0, propiedades.getUmbral()));
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(crearResumen(items, usuarioEmail, usuarioOid, "UMBRAL"));
    }

    /**
     * Barrido diario: agrupa TODO lo pendiente de cada usuario en un resumen,
     * aunque no llegue al umbral. Devuelve cuantos resumenes se generaron.
     */
    @Transactional
    public synchronized int generarResumenesPendientes() {
        List<String> usuarios = novedadRepository.findUsuariosConPendientes();
        int creados = 0;
        for (String email : usuarios) {
            List<Novedad> items = novedadRepository
                    .findByUsuarioEmailIgnoreCaseAndResumenIdIsNullOrderByFechaCreacionAsc(email);
            if (items.isEmpty()) {
                continue;
            }
            String oid = items.get(0).getUsuarioOid();
            crearResumen(items, email, oid, "DIARIO");
            creados++;
        }
        if (creados > 0) {
            log.info("Resumen diario: se generaron {} resumen(es) de novedades", creados);
        }
        return creados;
    }

    /** Resumenes del usuario para la pagina principal (mas reciente primero). */
    @Transactional(readOnly = true)
    public List<ResumenNovedadesResponse> listarResumenes(String usuarioEmail) {
        return resumenRepository.findByUsuarioEmailIgnoreCaseOrderByFechaCreacionDesc(usuarioEmail)
                .stream()
                .map(this::aResponse)
                .toList();
    }

    /** Descarta un resumen: borra el resumen y sus novedades asociadas. */
    @Transactional
    public void descartarResumen(String usuarioEmail, Long resumenId) {
        resumenRepository.findByIdAndUsuarioEmailIgnoreCase(resumenId, usuarioEmail)
                .ifPresent(resumen -> {
                    novedadRepository.deleteByResumenId(resumen.getId());
                    resumenRepository.delete(resumen);
                });
    }

    private ResumenNovedades crearResumen(List<Novedad> items, String usuarioEmail,
                                          String usuarioOid, String motivo) {
        List<String> comunas = comunasDistintas(items);
        ResumenNovedades resumen = new ResumenNovedades();
        resumen.setUsuarioEmail(usuarioEmail);
        resumen.setUsuarioOid(usuarioOid);
        resumen.setCantidad(items.size());
        resumen.setComunas(String.join(", ", comunas));
        resumen.setMotivo(motivo);
        // Se publica justo debajo; si el broker falla queda el log de error y
        // el mensaje en la DLQ cuando el consumidor no pueda procesarlo.
        resumen.setCorreoEnviado(true);
        ResumenNovedades guardado = resumenRepository.save(resumen);

        for (Novedad item : items) {
            item.setResumenId(guardado.getId());
        }
        novedadRepository.saveAll(items);

        publisher.publicarResumen(construirEvento(usuarioEmail, comunas, items));
        log.info("Resumen {} generado para {} ({} propiedades, motivo {}, comunas {})",
                guardado.getId(), usuarioEmail, items.size(), motivo, comunas);
        return guardado;
    }

    private NotificacionSistemaEvent construirEvento(String usuarioEmail,
                                                     List<String> comunas,
                                                     List<Novedad> items) {
        NotificacionSistemaEvent evento = new NotificacionSistemaEvent();
        evento.setDestinatarioEmail(usuarioEmail);
        evento.setCantidad(items.size());
        evento.setComunas(comunas);
        evento.setMensaje(items.size() == 1
                ? "Tienes 1 nueva propiedad en tus comunas"
                : "Tienes " + items.size() + " nuevas propiedades en tus comunas");
        evento.setItems(items.stream()
                .map(n -> new NotificacionItem(n.getPropiedadId(), n.getTitulo(), n.getComuna(),
                        n.getPrecio(), n.getTipoPrecio(), n.getUbicacion()))
                .toList());
        return evento;
    }

    private ResumenNovedadesResponse aResponse(ResumenNovedades resumen) {
        ResumenNovedadesResponse dto = new ResumenNovedadesResponse();
        dto.setId(resumen.getId());
        dto.setFechaCreacion(resumen.getFechaCreacion());
        dto.setCantidad(resumen.getCantidad());
        List<String> comunas = partirComunas(resumen.getComunas());
        dto.setComunas(comunas);
        dto.setTitulo(titulo(resumen.getCantidad(), comunas));
        dto.setItems(novedadRepository.findByResumenIdOrderByFechaCreacionAsc(resumen.getId())
                .stream()
                .map(n -> new ResumenNovedadesResponse.ItemNovedadResponse(
                        n.getPropiedadId(), n.getTitulo(), n.getComuna(),
                        n.getPrecio(), n.getTipoPrecio(), n.getUbicacion()))
                .toList());
        return dto;
    }

    private String titulo(Integer cantidad, List<String> comunas) {
        String donde = comunas.isEmpty() ? "tus comunas" : String.join(", ", comunas);
        int n = cantidad == null ? 0 : cantidad;
        return n == 1
                ? "1 nueva propiedad en " + donde
                : n + " nuevas propiedades en " + donde;
    }

    private List<String> comunasDistintas(List<Novedad> items) {
        Set<String> comunas = new LinkedHashSet<>();
        for (Novedad item : items) {
            if (item.getComuna() != null && !item.getComuna().isBlank()) {
                comunas.add(item.getComuna());
            }
        }
        return List.copyOf(comunas);
    }

    private List<String> partirComunas(String comunas) {
        if (comunas == null || comunas.isBlank()) {
            return List.of();
        }
        return List.of(comunas.split("\\s*,\\s*"));
    }
}
