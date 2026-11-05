package cl.inmobiliarias.wishlist.service;

import cl.inmobiliarias.wishlist.dto.FavoritoRequest;
import cl.inmobiliarias.wishlist.model.AlertaComuna;
import cl.inmobiliarias.wishlist.model.Favorito;
import cl.inmobiliarias.wishlist.repository.AlertaComunaRepository;
import cl.inmobiliarias.wishlist.repository.FavoritoRepository;
import cl.inmobiliarias.wishlist.util.Comunas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Logica de favoritos y alertas por comuna.
 */
@Service
public class WishlistService {

    private static final Logger log = LoggerFactory.getLogger(WishlistService.class);

    private final FavoritoRepository favoritoRepository;
    private final AlertaComunaRepository alertaComunaRepository;

    public WishlistService(FavoritoRepository favoritoRepository,
                           AlertaComunaRepository alertaComunaRepository) {
        this.favoritoRepository = favoritoRepository;
        this.alertaComunaRepository = alertaComunaRepository;
    }

    /** Lista los favoritos de un usuario. */
    @Transactional(readOnly = true)
    public List<Favorito> listarFavoritos(String email) {
        return favoritoRepository.findByUsuarioEmailIgnoreCase(email);
    }

    /**
     * Agrega un favorito.
     *
     * Extrae la comuna desde la ubicacion y, si se encuentra, crea
     * automaticamente la alerta de esa comuna si el usuario aun no la sigue.
     * Asi el usuario empieza a recibir avisos sin pasos extra.
     */
    @Transactional
    public Favorito agregarFavorito(String email, String oid, FavoritoRequest request) {
        if (favoritoRepository.existsByUsuarioEmailIgnoreCaseAndPropiedadId(email, request.getPropiedadId())) {
            throw new RuntimeException("La propiedad ya esta en favoritos");
        }
        Favorito favorito = new Favorito();
        favorito.setUsuarioEmail(email);
        favorito.setUsuarioOid(oid);
        favorito.setPropiedadId(request.getPropiedadId());
        favorito.setTitulo(request.getTitulo());
        favorito.setPrecio(request.getPrecio());
        favorito.setTipoPrecio(request.getTipoPrecio());
        favorito.setUbicacion(request.getUbicacion());

        String comuna = Comunas.extraerComuna(request.getUbicacion());
        favorito.setComuna(comuna);
        Favorito guardado = favoritoRepository.save(favorito);

        if (comuna != null && !alertaComunaRepository.existsByUsuarioEmailIgnoreCaseAndComunaIgnoreCase(email, comuna)) {
            AlertaComuna alerta = new AlertaComuna(email, oid, comuna);
            alertaComunaRepository.save(alerta);
            log.info("Alerta automatica creada: usuario={} comuna={}", email, comuna);
        }
        return guardado;
    }

    /** Elimina un favorito. Si no existe, no hace nada. */
    @Transactional
    public void eliminarFavorito(String email, Long propiedadId) {
        favoritoRepository.deleteByUsuarioEmailIgnoreCaseAndPropiedadId(email, propiedadId);
    }

    /** Lista las comunas que sigue un usuario. */
    @Transactional(readOnly = true)
    public List<AlertaComuna> listarAlertas(String email) {
        return alertaComunaRepository.findByUsuarioEmailIgnoreCase(email);
    }

    /**
     * Empieza a seguir una comuna.
     * La comuna llega en texto libre y se valida contra la lista canonica.
     */
    @Transactional
    public AlertaComuna seguirComuna(String email, String oid, String comunaCruda) {
        String canonica = Comunas.normalizarComuna(comunaCruda);
        if (canonica == null) {
            throw new RuntimeException("Comuna no valida: " + comunaCruda);
        }
        if (alertaComunaRepository.existsByUsuarioEmailIgnoreCaseAndComunaIgnoreCase(email, canonica)) {
            throw new RuntimeException("Ya sigues la comuna " + canonica);
        }
        AlertaComuna alerta = new AlertaComuna(email, oid, canonica);
        return alertaComunaRepository.save(alerta);
    }

    /** Deja de seguir una comuna. Si no la seguia, no hace nada. */
    @Transactional
    public void dejarDeSeguirComuna(String email, String comuna) {
        String canonica = Comunas.normalizarComuna(comuna);
        String aBorrar = canonica != null ? canonica : comuna;
        alertaComunaRepository.deleteByUsuarioEmailIgnoreCaseAndComunaIgnoreCase(email, aBorrar);
    }

    /** Lista de comunas soportadas por el negocio. */
    public List<String> listarComunas() {
        return Comunas.listaComunas();
    }
}
