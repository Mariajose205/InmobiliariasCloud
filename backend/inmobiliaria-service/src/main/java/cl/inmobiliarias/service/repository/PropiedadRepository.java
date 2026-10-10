package cl.inmobiliarias.service.repository;

import cl.inmobiliarias.service.model.Propiedad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropiedadRepository extends JpaRepository<Propiedad, Long> {

    List<Propiedad> findByCategoriaIgnoreCase(String categoria);

    List<Propiedad> findByTipoOperacion(String tipoOperacion);

    List<Propiedad> findByCreadoPorEmailIgnoreCase(String email);

    List<Propiedad> findByCreadoPorOidIgnoreCase(String oid);
}
