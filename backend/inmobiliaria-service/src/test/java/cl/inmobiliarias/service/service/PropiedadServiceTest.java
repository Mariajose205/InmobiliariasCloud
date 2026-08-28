package cl.inmobiliarias.service.service;

import cl.inmobiliarias.service.model.Propiedad;
import cl.inmobiliarias.service.repository.PropiedadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropiedadServiceTest {

    @Mock
    private PropiedadRepository repository;

    @InjectMocks
    private PropiedadService service;

    private Propiedad propiedad;

    @BeforeEach
    void setUp() {
        propiedad = new Propiedad(
                "Casa de prueba", "Casa", "Venta", 100000000L,
                "/ Valor Total", 30000L, "Providencia",
                3, 2, 1, 0, "", List.of("imagenes.png"));
        propiedad.setId(1L);
    }

    @Test
    void listarDevuelveTodasLasPropiedades() {
        when(repository.findAll()).thenReturn(List.of(propiedad));

        var resultado = service.listar();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getTitulo()).isEqualTo("Casa de prueba");
    }

    @Test
    void listarPorCategoriaFiltraLasPropiedades() {
        when(repository.findByCategoria("Casa")).thenReturn(List.of(propiedad));

        var resultado = service.listarPorCategoria("Casa");

        assertThat(resultado).hasSize(1);
        verify(repository).findByCategoria("Casa");
    }

    @Test
    void obtenerDevuelvePropiedadExistente() {
        when(repository.findById(1L)).thenReturn(Optional.of(propiedad));

        var resultado = service.obtener(1L);

        assertThat(resultado.getId()).isEqualTo(1L);
        assertThat(resultado.getTitulo()).isEqualTo("Casa de prueba");
    }

    @Test
    void obtenerLanzaErrorSiNoExiste() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    @Test
    void crearGuardaUnaPropiedadNueva() {
        when(repository.save(any(Propiedad.class))).thenReturn(propiedad);

        var resultado = service.crear(propiedad);

        assertThat(resultado).isNotNull();
        verify(repository).save(propiedad);
    }

    @Test
    void actualizarModificaLosDatosDeUnaPropiedadExistente() {
        Propiedad cambios = new Propiedad(
                "Titulo modificado", "Departamento", "Arriendo", 50000000L,
                "/ Mensual", 10000L, "Ñuñoa",
                2, 1, 0, 0, "https://youtube.com", List.of("nueva.jpg"));

        when(repository.findById(1L)).thenReturn(Optional.of(propiedad));
        when(repository.save(any(Propiedad.class))).thenAnswer(inv -> inv.getArgument(0));

        var resultado = service.actualizar(1L, cambios);

        assertThat(resultado.getTitulo()).isEqualTo("Titulo modificado");
        assertThat(resultado.getCategoria()).isEqualTo("Departamento");
        assertThat(resultado.getTipoOperacion()).isEqualTo("Arriendo");
        assertThat(resultado.getPrecio()).isEqualTo(50000000L);
        verify(repository).save(propiedad);
    }

    @Test
    void actualizarLanzaErrorSiLaPropiedadNoExiste() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar(5L, propiedad))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void eliminarBorraUnaPropiedadExistente() {
        when(repository.existsById(1L)).thenReturn(true);

        service.eliminar(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void eliminarLanzaErrorSiLaPropiedadNoExiste() {
        when(repository.existsById(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.eliminar(7L))
                .isInstanceOf(RuntimeException.class);
    }
}
