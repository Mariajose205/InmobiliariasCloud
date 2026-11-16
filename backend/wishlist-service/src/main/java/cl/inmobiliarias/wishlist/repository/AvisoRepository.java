package cl.inmobiliarias.wishlist.repository;

import cl.inmobiliarias.wishlist.model.Aviso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso a los avisos de propiedades eliminadas.
 */
public interface AvisoRepository extends JpaRepository<Aviso, Long> {

    List<Aviso> findByUsuarioEmailIgnoreCaseOrderByFechaCreacionDesc(String usuarioEmail);

    boolean existsByUsuarioEmailIgnoreCaseAndPropiedadId(String usuarioEmail, Long propiedadId);

    void deleteByIdAndUsuarioEmailIgnoreCase(Long id, String usuarioEmail);

    void deleteByUsuarioEmailIgnoreCaseAndPropiedadId(String usuarioEmail, Long propiedadId);
}
