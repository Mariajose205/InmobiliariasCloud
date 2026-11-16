package cl.inmobiliarias.wishlist.service;

import cl.inmobiliarias.wishlist.dto.AvisoResponse;
import cl.inmobiliarias.wishlist.dto.PropiedadEliminadaEvent;
import cl.inmobiliarias.wishlist.model.Aviso;
import cl.inmobiliarias.wishlist.model.Favorito;
import cl.inmobiliarias.wishlist.repository.AvisoRepository;
import cl.inmobiliarias.wishlist.repository.FavoritoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de la baja de propiedades: se crea un aviso por usuario afectado y se
 * borran sus favoritos. Sin Kafka ni base de datos real.
 */
@ExtendWith(MockitoExtension.class)
class AvisoServiceTest {

    @Mock
    private FavoritoRepository favoritoRepository;

    @Mock
    private AvisoRepository avisoRepository;

    @InjectMocks
    private AvisoService service;

    @Test
    void creaUnAvisoPorUsuarioYborraLosFavoritos() {
        PropiedadEliminadaEvent evento = new PropiedadEliminadaEvent();
        evento.setPropiedadId(7L);
        evento.setTitulo("Casa en Quillota");
        evento.setTipoOperacion("Venta");
        evento.setComuna("Quillota");

        when(favoritoRepository.findByPropiedadId(7L)).thenReturn(List.of(
                favorito("u1@x.cl", "oid1", 7L), favorito("u2@x.cl", "oid2", 7L)));
        when(avisoRepository.existsByUsuarioEmailIgnoreCaseAndPropiedadId(any(), eq(7L)))
                .thenReturn(false);

        int creados = service.registrarEliminacion(evento);

        assertEquals(2, creados);
        ArgumentCaptor<Aviso> captor = ArgumentCaptor.forClass(Aviso.class);
        verify(avisoRepository, times(2)).save(captor.capture());
        assertEquals("u1@x.cl", captor.getAllValues().get(0).getUsuarioEmail());
        assertEquals("u2@x.cl", captor.getAllValues().get(1).getUsuarioEmail());
        assertTrue(captor.getAllValues().get(0).getMensaje().contains("ya fue vendida"));
        assertTrue(captor.getAllValues().get(0).getMensaje().contains("se eliminó automáticamente"));
        verify(favoritoRepository).deleteByPropiedadId(7L);
    }

    @Test
    void usaArrendadaCuandoElTipoDeOperacionEsArriendo() {
        PropiedadEliminadaEvent evento = new PropiedadEliminadaEvent();
        evento.setPropiedadId(8L);
        evento.setTitulo("Depto");
        evento.setTipoOperacion("Arriendo");

        when(favoritoRepository.findByPropiedadId(8L))
                .thenReturn(List.of(favorito("u1@x.cl", "oid1", 8L)));
        when(avisoRepository.existsByUsuarioEmailIgnoreCaseAndPropiedadId(any(), eq(8L)))
                .thenReturn(false);

        service.registrarEliminacion(evento);

        ArgumentCaptor<Aviso> captor = ArgumentCaptor.forClass(Aviso.class);
        verify(avisoRepository).save(captor.capture());
        assertTrue(captor.getValue().getMensaje().contains("ya fue arrendada"));
    }

    @Test
    void siNadieTeniaLaPropiedadNoCreaAvisos() {
        PropiedadEliminadaEvent evento = new PropiedadEliminadaEvent();
        evento.setPropiedadId(9L);
        evento.setTitulo("Casa");

        when(favoritoRepository.findByPropiedadId(9L)).thenReturn(List.of());

        assertEquals(0, service.registrarEliminacion(evento));
        verify(avisoRepository, never()).save(any());
        verify(favoritoRepository, never()).deleteByPropiedadId(any());
    }

    @Test
    void siElAvisoYaExisteNoLoDuplicaPeroIgualLimpiaElFavorito() {
        PropiedadEliminadaEvent evento = new PropiedadEliminadaEvent();
        evento.setPropiedadId(10L);
        evento.setTitulo("Casa");

        when(favoritoRepository.findByPropiedadId(10L))
                .thenReturn(List.of(favorito("u1@x.cl", "oid1", 10L)));
        when(avisoRepository.existsByUsuarioEmailIgnoreCaseAndPropiedadId("u1@x.cl", 10L))
                .thenReturn(true);

        assertEquals(0, service.registrarEliminacion(evento));
        verify(avisoRepository, never()).save(any());
        verify(favoritoRepository).deleteByPropiedadId(10L);
    }

    @Test
    void eventoInvalidoNoTocaNada() {
        assertEquals(0, service.registrarEliminacion(null));
        verify(favoritoRepository, never()).findByPropiedadId(any());
    }

    @Test
    void listarDevuelveLosAvisosDelUsuario() {
        Aviso aviso = new Aviso("u1@x.cl", "oid1", 1L, "Casa", "Quillota",
                "Venta", "La propiedad \"Casa\" ya fue vendida.");
        aviso.setId(5L);
        aviso.setFechaCreacion(Instant.now());
        when(avisoRepository.findByUsuarioEmailIgnoreCaseOrderByFechaCreacionDesc("u1@x.cl"))
                .thenReturn(List.of(aviso));

        List<AvisoResponse> lista = service.listar("u1@x.cl");

        assertEquals(1, lista.size());
        assertEquals(5L, lista.get(0).getId());
        assertEquals("Casa", lista.get(0).getTitulo());
    }

    private Favorito favorito(String email, String oid, Long propiedadId) {
        Favorito favorito = new Favorito();
        favorito.setUsuarioEmail(email);
        favorito.setUsuarioOid(oid);
        favorito.setPropiedadId(propiedadId);
        favorito.setTitulo("Casa");
        favorito.setComuna("Quillota");
        return favorito;
    }
}
