package cl.inmobiliarias.wishlist.service;

import cl.inmobiliarias.wishlist.dto.AvisoResponse;
import cl.inmobiliarias.wishlist.dto.PropiedadEliminadaEvent;
import cl.inmobiliarias.wishlist.model.Aviso;
import cl.inmobiliarias.wishlist.model.Favorito;
import cl.inmobiliarias.wishlist.repository.AvisoRepository;
import cl.inmobiliarias.wishlist.repository.FavoritoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Avisos de propiedades eliminadas.
 *
 * Cuando un administrador o corredor borra una propiedad, inmobiliaria-service
 * publica el evento en {@code propiedades-eliminadas}. Este servicio:
 *
 *   1. busca todos los usuarios que tenian esa propiedad en favoritos,
 *   2. guarda un aviso personal por usuario con el texto
 *      "ya fue vendida/arrendada y se elimino de tus favoritos", y
 *   3. borra los favoritos de esa propiedad.
 *
 * Todo ocurre en una sola transaccion: o se limpia el favorito y queda el
 * aviso, o no se toca nada (el consumidor reintenta y, al agotar, va a la DLQ).
 */
@Service
public class AvisoService {

    private static final Logger log = LoggerFactory.getLogger(AvisoService.class);

    private final FavoritoRepository favoritoRepository;
    private final AvisoRepository avisoRepository;

    public AvisoService(FavoritoRepository favoritoRepository,
                        AvisoRepository avisoRepository) {
        this.favoritoRepository = favoritoRepository;
        this.avisoRepository = avisoRepository;
    }

    /**
     * Registra la eliminacion para todos los usuarios que tenian la propiedad
     * en favoritos. Devuelve cuantos avisos nuevos se crearon.
     */
    @Transactional
    public int registrarEliminacion(PropiedadEliminadaEvent evento) {
        if (evento == null || evento.getPropiedadId() == null) {
            return 0;
        }

        List<Favorito> favoritos = favoritoRepository.findByPropiedadId(evento.getPropiedadId());
        if (favoritos.isEmpty()) {
            log.info("Propiedad {} eliminada: ningun usuario la tenia en favoritos",
                    evento.getPropiedadId());
            return 0;
        }

        String titulo = texto(evento.getTitulo(), "Propiedad");
        String mensaje = construirMensaje(titulo, evento.getTipoOperacion());
        Set<String> yaAvisados = new HashSet<>();
        int creados = 0;

        for (Favorito favorito : favoritos) {
            String email = favorito.getUsuarioEmail();
            if (email == null || email.isBlank()
                    || !yaAvisados.add(email.toLowerCase(Locale.ROOT))) {
                continue;
            }
            try {
                if (avisoRepository.existsByUsuarioEmailIgnoreCaseAndPropiedadId(
                        email, evento.getPropiedadId())) {
                    continue;
                }
                String comuna = favorito.getComuna() != null && !favorito.getComuna().isBlank()
                        ? favorito.getComuna() : evento.getComuna();
                avisoRepository.save(new Aviso(
                        email,
                        favorito.getUsuarioOid(),
                        evento.getPropiedadId(),
                        titulo,
                        comuna,
                        evento.getTipoOperacion(),
                        mensaje));
                creados++;
                log.info("Aviso de propiedad eliminada id={} creado para {}",
                        evento.getPropiedadId(), email);
            } catch (RuntimeException ex) {
                // La restriccion unica (usuario, propiedad) evita duplicados si
                // el evento se reentrega: se ignora y se sigue con el resto.
                log.warn("No se pudo guardar el aviso de la propiedad {} para {}: {}",
                        evento.getPropiedadId(), email, ex.getMessage());
            }
        }

        // El favorito deja de existir en la misma transaccion que el aviso.
        favoritoRepository.deleteByPropiedadId(evento.getPropiedadId());
        log.info("Propiedad {} eliminada: {} favorito(s) limpiados, {} aviso(s) creados",
                evento.getPropiedadId(), favoritos.size(), creados);
        return creados;
    }

    /** Avisos pendientes del usuario (mas reciente primero). */
    @Transactional(readOnly = true)
    public List<AvisoResponse> listar(String usuarioEmail) {
        return avisoRepository.findByUsuarioEmailIgnoreCaseOrderByFechaCreacionDesc(usuarioEmail)
                .stream()
                .map(a -> new AvisoResponse(a.getId(), a.getPropiedadId(), a.getTitulo(),
                        a.getComuna(), a.getTipoOperacion(), a.getMensaje(), a.getFechaCreacion()))
                .toList();
    }

    /** El usuario descarta el aviso: solo se borra si es suyo. */
    @Transactional
    public void descartar(String usuarioEmail, Long avisoId) {
        avisoRepository.deleteByIdAndUsuarioEmailIgnoreCase(avisoId, usuarioEmail);
    }

    /**
     * Texto que ve el cliente. Usa el tipo de operacion de la propiedad para
     * ser preciso: "ya fue vendida" o "ya fue arrendada".
     */
    static String construirMensaje(String titulo, String tipoOperacion) {
        String estado;
        String tipo = tipoOperacion == null ? "" : tipoOperacion.toLowerCase(Locale.ROOT);
        if (tipo.contains("venta")) {
            estado = "ya fue vendida";
        } else if (tipo.contains("arriendo") || tipo.contains("arrend")) {
            estado = "ya fue arrendada";
        } else {
            estado = "ya fue vendida o arrendada";
        }
        return "La propiedad \"" + titulo + "\" " + estado
                + " y se eliminó automáticamente de tus favoritos.";
    }

    private String texto(String valor, String porDefecto) {
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }
}
