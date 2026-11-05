package cl.inmobiliarias.wishlist.util;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utilidad de comunas soportadas por el servicio.
 *
 * La lista final es la del dominio del negocio. La comparacion siempre es
 * insensible a mayusculas/minusculas y a tildes, porque la ubicacion llega
 * como texto libre desde el catalogo de propiedades ("Vina del Mar, Valparaiso",
 * "QUILLOTA", "Quilpué", ...).
 *
 * Los metodos devuelven null cuando el texto no coincide con ninguna comuna
 * de la lista, para que el llamador decida que hacer en ese caso.
 */
public final class Comunas {

    /** Lista canonica, tal como se guarda en la BD y en la clave Kafka. */
    public static final List<String> COMUNAS = List.of(
            "Quillota",
            "Viña del Mar",
            "Quilpué",
            "Villa Alemana",
            "El Belloto",
            "La Cruz",
            "Calera",
            "Valparaíso");

    /**
     * Mapa de comuna normalizada a comuna canonica, calculado una vez.
     * La clave normalizada quita tildes, pasa a minusculas y recorta espacios.
     * Se usa un ConcurrentHashMap porque puede leerse desde varios hilos
     * consumidores de Kafka a la vez.
     */
    private static final Map<String, String> CANONICA_POR_NORMALIZADA = new ConcurrentHashMap<>();

    static {
        for (String comuna : COMUNAS) {
            CANONICA_POR_NORMALIZADA.put(normalizarBase(comuna), comuna);
        }
    }

    private Comunas() {
    }

    /**
     * Busca en el texto la primera comuna de la lista que aparece como
     * subcadena (comparacion normalizada).
     *
     * Ejemplo: "Av. Libertad 123, Viña del Mar" devuelve "Viña del Mar".
     * Si el texto es null/vacio o no contiene ninguna comuna, devuelve null.
     */
    public static String extraerComuna(String ubicacion) {
        if (ubicacion == null || ubicacion.isBlank()) {
            return null;
        }
        String normalizada = normalizarBase(ubicacion);
        for (String comuna : COMUNAS) {
            if (normalizada.contains(normalizarBase(comuna))) {
                return comuna;
            }
        }
        return null;
    }

    /**
     * Normaliza una comuna escrita por un usuario a su forma canonica.
     *
     * Ejemplo: "  QUILLOTA " o "vinA DEL mar" (con o sin tildes)
     * devuelven "Quillota" y "Viña del Mar" respectivamente.
     * Si no coincide con la lista, devuelve null.
     */
    public static String normalizarComuna(String comuna) {
        if (comuna == null || comuna.isBlank()) {
            return null;
        }
        return CANONICA_POR_NORMALIZADA.get(normalizarBase(comuna.trim()));
    }

    /** Devuelve la lista inmutable de comunas soportadas. */
    public static List<String> listaComunas() {
        return COMUNAS;
    }

    /**
     * Base de la normalizacion: recorta, pasa a minusculas y quita tildes
     * (via Normalizer NFD + removida de diacriticos).
     */
    private static String normalizarBase(String texto) {
        String minusculas = texto.trim().toLowerCase(Locale.ROOT);
        String sinTildes = Normalizer.normalize(minusculas, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        // Cola las dobles espacios para que "vina  del mar" tambien coincida.
        return sinTildes.replaceAll("\\s+", " ");
    }
}
