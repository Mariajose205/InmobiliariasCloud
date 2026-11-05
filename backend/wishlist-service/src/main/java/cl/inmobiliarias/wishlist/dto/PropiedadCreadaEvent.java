package cl.inmobiliarias.wishlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Evento que publica inmobiliaria-service cuando se crea una propiedad.
 *
 * Se recibe en PropiedadNuevaNotificador (topic propiedades-nuevas).
 * La clave Kafka es la comuna canonica, asi que cada comuna se procesa
 * en su propia particion.
 */
public class PropiedadCreadaEvent {

    @NotNull(message = "La propiedadId es obligatoria")
    private Long propiedadId;

    @NotBlank(message = "El titulo es obligatorio")
    private String titulo;

    private String categoria;

    private String tipoOperacion;

    private Long precio;

    private String tipoPrecio;

    private String ubicacion;

    private String comuna;

    private String creadoPorEmail;

    private String fechaCreacion;

    public PropiedadCreadaEvent() {
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

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getTipoOperacion() {
        return tipoOperacion;
    }

    public void setTipoOperacion(String tipoOperacion) {
        this.tipoOperacion = tipoOperacion;
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

    public String getComuna() {
        return comuna;
    }

    public void setComuna(String comuna) {
        this.comuna = comuna;
    }

    public String getCreadoPorEmail() {
        return creadoPorEmail;
    }

    public void setCreadoPorEmail(String creadoPorEmail) {
        this.creadoPorEmail = creadoPorEmail;
    }

    public String getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(String fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
