package cl.inmobiliarias.wishlist.dto.admin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resumen simplificado de un topico Kafka para la pantalla de admin.
 * Solo se exponen los datos utiles y no el detalle completo de particiones.
 */
public class TopicInfoResponse {

    private String nombre;

    private int particiones;

    private short factorReplicacion;

    private Map<String, String> configs = new LinkedHashMap<>();

    public TopicInfoResponse() {
    }

    public TopicInfoResponse(String nombre, int particiones, short factorReplicacion, Map<String, String> configs) {
        this.nombre = nombre;
        this.particiones = particiones;
        this.factorReplicacion = factorReplicacion;
        this.configs = configs != null ? configs : new LinkedHashMap<>();
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getParticiones() {
        return particiones;
    }

    public void setParticiones(int particiones) {
        this.particiones = particiones;
    }

    public short getFactorReplicacion() {
        return factorReplicacion;
    }

    public void setFactorReplicacion(short factorReplicacion) {
        this.factorReplicacion = factorReplicacion;
    }

    public Map<String, String> getConfigs() {
        return configs;
    }

    public void setConfigs(Map<String, String> configs) {
        this.configs = configs;
    }
}
