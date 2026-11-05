package cl.inmobiliarias.wishlist.repository;

import cl.inmobiliarias.wishlist.model.Favorito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso a la tabla de favoritos.
 */
public interface FavoritoRepository extends JpaRepository<Favorito, Long> {

    List<Favorito> findByUsuarioEmailIgnoreCase(String usuarioEmail);

    boolean existsByUsuarioEmailIgnoreCaseAndPropiedadId(String usuarioEmail, Long propiedadId);

    void deleteByUsuarioEmailIgnoreCaseAndPropiedadId(String usuarioEmail, Long propiedadId);
}
