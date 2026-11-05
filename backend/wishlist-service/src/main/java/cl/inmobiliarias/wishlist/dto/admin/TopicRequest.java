package cl.inmobiliarias.wishlist.dto.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo del POST /api/admin/kafka/topicos.
 *
 * Los campos opcionales tienen valores por defecto en KafkaAdminService
 * (3 particiones, factor 1, retencion de 7 dias, cleanup delete).
 */
public class TopicRequest {

    @NotBlank(message = "El nombre del topico es obligatorio")
    private String nombre;

    private Integer particiones;

    private Integer factorReplicacion;

    private Long retencionMs;

    private String cleanupPolicy;

    public TopicRequest() {
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Integer getParticiones() {
        return particiones;
    }

    public void setParticiones(Integer particiones) {
        this.particiones = particiones;
    }

    public Integer getFactorReplicacion() {
        return factorReplicacion;
    }

    public void setFactorReplicacion(Integer factorReplicacion) {
        this.factorReplicacion = factorReplicacion;
    }

    public Long getRetencionMs() {
        return retencionMs;
    }

    public void setRetencionMs(Long retencionMs) {
        this.retencionMs = retencionMs;
    }

    public String getCleanupPolicy() {
        return cleanupPolicy;
    }

    public void setCleanupPolicy(String cleanupPolicy) {
        this.cleanupPolicy = cleanupPolicy;
    }
}
