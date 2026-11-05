package cl.inmobiliarias.service.util;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utilidad de comunas soportadas por el negocio.
 *
 * La comparacion es insensible a mayusculas/minusculas y a tildes porque la
 * ubicacion llega como texto libre desde el catalogo ("Vina del Mar",
 * "QUILLOTA", "Quilpué", ...). La comuna canonica resultante se usa como
 * CLAVE Kafka en el topic propiedades-nuevas, por lo que todos los mensajes
 * de una misma comuna caen siempre en la misma particion.
 */
public final class Comunas {

    public static final List<String> COMUNAS = List.of(
            "Quillota",
            "Viña del Mar",
            "Quilpué",
            "Villa Alemana",
            "El Belloto",
            "La Cruz",
            "Calera",
            "Valparaíso");

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
     * subcadena (comparacion normalizada). Si no hay coincidencia o el texto
     * es vacio, devuelve null (el publicador usa "OTROS" como clave).
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

    public static String normalizarComuna(String comuna) {
        if (comuna == null || comuna.isBlank()) {
            return null;
        }
        return CANONICA_POR_NORMALIZADA.get(normalizarBase(comuna.trim()));
    }

    public static List<String> listaComunas() {
        return COMUNAS;
    }

    private static String normalizarBase(String texto) {
        String minusculas = texto.trim().toLowerCase(Locale.ROOT);
        String sinTildes = Normalizer.normalize(minusculas, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.replaceAll("\\s+", " ");
    }
}
