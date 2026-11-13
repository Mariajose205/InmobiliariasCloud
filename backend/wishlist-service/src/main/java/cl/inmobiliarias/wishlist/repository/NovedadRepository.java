package cl.inmobiliarias.wishlist.repository;

import cl.inmobiliarias.wishlist.model.Novedad;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Acceso a las novedades pendientes y a las que ya forman parte de un resumen.
 */
public interface NovedadRepository extends JpaRepository<Novedad, Long> {

    List<Novedad> findByUsuarioEmailIgnoreCaseOrderByFechaCreacionDesc(String usuarioEmail);

    void deleteByUsuarioEmailIgnoreCaseAndPropiedadId(String usuarioEmail, Long propiedadId);

    /** Novedades aun no incluidas en ningun resumen (las mas antiguas primero). */
    List<Novedad> findByUsuarioEmailIgnoreCaseAndResumenIdIsNullOrderByFechaCreacionAsc(String usuarioEmail);

    /** Version paginada: permite tomar solo las `umbral` mas antiguas. */
    List<Novedad> findByUsuarioEmailIgnoreCaseAndResumenIdIsNullOrderByFechaCreacionAsc(
            String usuarioEmail, Pageable pageable);

    long countByUsuarioEmailIgnoreCaseAndResumenIdIsNull(String usuarioEmail);

    /** Usuarios con al menos una novedad pendiente (para el barrido diario). */
    @Query("select distinct n.usuarioEmail from Novedad n where n.resumenId is null")
    List<String> findUsuariosConPendientes();

    List<Novedad> findByResumenIdOrderByFechaCreacionAsc(Long resumenId);

    void deleteByResumenId(Long resumenId);
}
