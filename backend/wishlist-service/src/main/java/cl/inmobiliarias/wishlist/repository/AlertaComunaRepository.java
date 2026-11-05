package cl.inmobiliarias.wishlist.repository;

import cl.inmobiliarias.wishlist.model.AlertaComuna;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso a la tabla de alertas por comuna.
 */
public interface AlertaComunaRepository extends JpaRepository<AlertaComuna, Long> {

    List<AlertaComuna> findByUsuarioEmailIgnoreCase(String usuarioEmail);

    List<AlertaComuna> findByComunaIgnoreCase(String comuna);

    boolean existsByUsuarioEmailIgnoreCaseAndComunaIgnoreCase(String usuarioEmail, String comuna);

    void deleteByUsuarioEmailIgnoreCaseAndComunaIgnoreCase(String usuarioEmail, String comuna);
}
