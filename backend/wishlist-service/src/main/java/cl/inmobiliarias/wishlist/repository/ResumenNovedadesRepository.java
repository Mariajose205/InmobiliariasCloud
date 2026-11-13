package cl.inmobiliarias.wishlist.repository;

import cl.inmobiliarias.wishlist.model.ResumenNovedades;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a los resumenes (digests) de novedades.
 */
public interface ResumenNovedadesRepository extends JpaRepository<ResumenNovedades, Long> {

    List<ResumenNovedades> findByUsuarioEmailIgnoreCaseOrderByFechaCreacionDesc(String usuarioEmail);

    Optional<ResumenNovedades> findByIdAndUsuarioEmailIgnoreCase(Long id, String usuarioEmail);
}
