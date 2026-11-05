package cl.inmobiliarias.wishlist.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests de normalizacion/extraccion de comunas (sin Kafka, sin Spring).
 */
class ComunasTest {

    @Test
    void extraerComunaCaseInsensitive() {
        assertEquals("Quillota", Comunas.extraerComuna("Av. Scheybar 123, QUILLOTA"));
        assertEquals("Quillota", Comunas.extraerComuna("quillota centro"));
        assertEquals("Viña del Mar", Comunas.extraerComuna("VINA DEL MAR, Valparaiso"));
    }

    @Test
    void extraerComunaConTildes() {
        assertEquals("Viña del Mar", Comunas.extraerComuna("Viña del Mar centro"));
        assertEquals("Quilpué", Comunas.extraerComuna("Quilpue, sector norte"));
        assertEquals("Valparaíso", Comunas.extraerComuna("Calle Condell, VALPARAISO"));
    }

    @Test
    void extraerComunaSinCoincidenciaDevuelveNull() {
        assertNull(Comunas.extraerComuna("Santiago centro"));
        assertNull(Comunas.extraerComuna(null));
        assertNull(Comunas.extraerComuna(""));
        assertNull(Comunas.extraerComuna("   "));
    }

    @Test
    void normalizarComunaCanoniza() {
        assertEquals("Quillota", Comunas.normalizarComuna("  QUILLOTA  "));
        assertEquals("Viña del Mar", Comunas.normalizarComuna("vina del mar"));
        assertEquals("Viña del Mar", Comunas.normalizarComuna("Viña del Mar"));
        assertEquals("Quilpué", Comunas.normalizarComuna("QUILPUE"));
        assertEquals("Calera", Comunas.normalizarComuna("calera"));
        assertEquals("La Cruz", Comunas.normalizarComuna("la cruz"));
    }

    @Test
    void normalizarComunaInvalidaDevuelveNull() {
        assertNull(Comunas.normalizarComuna("Santiago"));
        assertNull(Comunas.normalizarComuna(null));
        assertNull(Comunas.normalizarComuna(""));
    }

    @Test
    void listaComunasContieneLasEsperadas() {
        assertEquals(8, Comunas.listaComunas().size());
        assertEquals("Quillota", Comunas.listaComunas().get(0));
    }
}
