package cl.inmobiliarias.service.service;

import cl.inmobiliarias.service.dto.InformeSolicitudMsg;
import cl.inmobiliarias.service.model.Propiedad;
import cl.inmobiliarias.service.repository.PropiedadRepository;
import cl.inmobiliarias.service.security.IdentidadInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica la regla diferenciada del informe: ADMIN ve todas las propiedades,
 * CORREDOR solo las que el mismo creo.
 */
@ExtendWith(MockitoExtension.class)
class InformeServiceTest {

    @Mock
    private PropiedadRepository repository;

    @InjectMocks
    private InformeService service;

    private Propiedad propiedad(String titulo, String creador) {
        Propiedad p = new Propiedad(titulo, "Casa", "Venta", 1000L,
                "/ Valor Total", 0L, "Santiago", 1, 1, 0, 0, "", List.of());
        p.setCreadoPorEmail(creador);
        return p;
    }

    @Test
    void adminRecibeTodasLasPropiedades() {
        when(repository.findAll()).thenReturn(List.of(
                propiedad("A", "admin@x.cl"),
                propiedad("B", "corredor@x.cl")));

        InformeSolicitudMsg msg = new InformeSolicitudMsg(
                "admin@x.cl", "oid-admin", IdentidadInterceptor.ROL_ADMIN, "2026-01-01T00:00:00Z");

        List<Propiedad> resultado = service.propiedadesPara(msg);

        assertThat(resultado).hasSize(2);
        verify(repository).findAll();
    }

    @Test
    void corredorRecibeSoloSusPropiedades() {
        when(repository.findByCreadoPorEmailIgnoreCase("corredor@x.cl")).thenReturn(List.of(
                propiedad("B", "corredor@x.cl")));

        InformeSolicitudMsg msg = new InformeSolicitudMsg(
                "corredor@x.cl", "oid-corredor", IdentidadInterceptor.ROL_CORREDOR, "2026-01-01T00:00:00Z");

        List<Propiedad> resultado = service.propiedadesPara(msg);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getCreadoPorEmail()).isEqualTo("corredor@x.cl");
        verify(repository).findByCreadoPorEmailIgnoreCase("corredor@x.cl");
    }
}
