package cl.inmobiliarias.wishlist.repository;

import cl.inmobiliarias.wishlist.model.Novedad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso a las novedades visibles en la pagina principal.
 */
public interface NovedadRepository extends JpaRepository<Novedad, Long> {

    List<Novedad> findByUsuarioEmailIgnoreCaseOrderByFechaCreacionDesc(String usuarioEmail);

    void deleteByUsuarioEmailIgnoreCaseAndPropiedadId(String usuarioEmail, Long propiedadId);
}
