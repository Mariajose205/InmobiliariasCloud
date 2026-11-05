package cl.inmobiliarias.wishlist.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo del POST /api/wishlist/alertas.
 *
 * La comuna llega tal como la escribio el usuario y el servicio la
 * normaliza a su forma canonica (ver Comunas.normalizarComuna).
 */
public class AlertaRequest {

    @NotBlank(message = "La comuna es obligatoria")
    private String comuna;

    public AlertaRequest() {
    }

    public String getComuna() {
        return comuna;
    }

    public void setComuna(String comuna) {
        this.comuna = comuna;
    }
}
