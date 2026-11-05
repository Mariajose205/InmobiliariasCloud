package cl.inmobiliarias.wishlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo del POST /api/wishlist/favoritos.
 *
 * Guardamos una copia minima de la propiedad para poder listar la wishlist
 * sin llamar al inmobiliaria-service en cada peticion.
 */
public class FavoritoRequest {

    @NotNull(message = "La propiedadId es obligatoria")
    private Long propiedadId;

    @NotBlank(message = "El titulo es obligatorio")
    private String titulo;

    private Long precio;

    private String tipoPrecio;

    @NotBlank(message = "La ubicacion es obligatoria")
    private String ubicacion;

    public FavoritoRequest() {
    }

    public Long getPropiedadId() {
        return propiedadId;
    }

    public void setPropiedadId(Long propiedadId) {
        this.propiedadId = propiedadId;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public Long getPrecio() {
        return precio;
    }

    public void setPrecio(Long precio) {
        this.precio = precio;
    }

    public String getTipoPrecio() {
        return tipoPrecio;
    }

    public void setTipoPrecio(String tipoPrecio) {
        this.tipoPrecio = tipoPrecio;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }
}
