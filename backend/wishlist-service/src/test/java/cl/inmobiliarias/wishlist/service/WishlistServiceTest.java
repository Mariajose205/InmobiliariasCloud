package cl.inmobiliarias.wishlist.service;

import cl.inmobiliarias.wishlist.dto.FavoritoRequest;
import cl.inmobiliarias.wishlist.model.AlertaComuna;
import cl.inmobiliarias.wishlist.model.Favorito;
import cl.inmobiliarias.wishlist.repository.AlertaComunaRepository;
import cl.inmobiliarias.wishlist.repository.FavoritoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del servicio con repositorios mockeados (sin Kafka, sin BD).
 */
@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    @Mock
    private FavoritoRepository favoritoRepository;

    @Mock
    private AlertaComunaRepository alertaComunaRepository;

    @InjectMocks
    private WishlistService service;

    @Test
    void agregarFavoritoAutoCreaAlertaDeLaComuna() {
        FavoritoRequest request = new FavoritoRequest();
        request.setPropiedadId(5L);
        request.setTitulo("Casa en Quillota");
        request.setPrecio(120000L);
        request.setTipoPrecio("CLP");
        request.setUbicacion("Av. Principal 123, Quillota");

        when(favoritoRepository.existsByUsuarioEmailIgnoreCaseAndPropiedadId(eq("a@b.cl"), eq(5L))).thenReturn(false);
        when(favoritoRepository.save(any(Favorito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alertaComunaRepository.existsByUsuarioEmailIgnoreCaseAndComunaIgnoreCase(eq("a@b.cl"), eq("Quillota")))
                .thenReturn(false);
        when(alertaComunaRepository.save(any(AlertaComuna.class))).thenAnswer(inv -> inv.getArgument(0));

        Favorito resultado = service.agregarFavorito("a@b.cl", "oid-1", request);

        assertNotNull(resultado);
        assertEquals("Quillota", resultado.getComuna());

        ArgumentCaptor<AlertaComuna> captor = ArgumentCaptor.forClass(AlertaComuna.class);
        verify(alertaComunaRepository).save(captor.capture());
        assertEquals("Quillota", captor.getValue().getComuna());
        assertEquals("a@b.cl", captor.getValue().getUsuarioEmail());
    }

    @Test
    void agregarFavoritoNoCreaAlertaSiYaLaTiene() {
        FavoritoRequest request = new FavoritoRequest();
        request.setPropiedadId(6L);
        request.setTitulo("Depto en el centro");
        request.setUbicacion("Centro, Quillota");

        when(favoritoRepository.existsByUsuarioEmailIgnoreCaseAndPropiedadId(anyString(), eq(6L))).thenReturn(false);
        when(favoritoRepository.save(any(Favorito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alertaComunaRepository.existsByUsuarioEmailIgnoreCaseAndComunaIgnoreCase(anyString(), eq("Quillota")))
                .thenReturn(true);

        service.agregarFavorito("a@b.cl", "oid-1", request);

        verify(alertaComunaRepository, never()).save(any());
    }

    @Test
    void seguirComunaValidaNormalizaComuna() {
        when(alertaComunaRepository.existsByUsuarioEmailIgnoreCaseAndComunaIgnoreCase(eq("a@b.cl"), eq("Quillota")))
                .thenReturn(false);
        when(alertaComunaRepository.save(any(AlertaComuna.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertaComuna alerta = service.seguirComuna("a@b.cl", "oid-1", "  quillota ");

        assertEquals("Quillota", alerta.getComuna());
    }

    @Test
    void seguirComunaInvalidaLanzaRuntimeException() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.seguirComuna("a@b.cl", "oid-1", "Santiago"));
        assertEquals("Comuna no valida: Santiago", ex.getMessage());
    }

    @Test
    void seguirComunaDuplicadaLanzaRuntimeException() {
        when(alertaComunaRepository.existsByUsuarioEmailIgnoreCaseAndComunaIgnoreCase(eq("a@b.cl"), eq("Quillota")))
                .thenReturn(true);

        assertThrows(RuntimeException.class, () -> service.seguirComuna("a@b.cl", "oid-1", "Quillota"));
    }
}
