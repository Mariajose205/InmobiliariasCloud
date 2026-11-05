package cl.inmobiliarias.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Evento que se publica en el topic {@code propiedades-nuevas} cuando se crea
 * una propiedad nueva en el catalogo.
 *
 * La CLAVE Kafka de este mensaje es la comuna canonica (Quillota, Vina del Mar,
 * ...) extraida de la ubicacion por {@code Comunas}. Kafka enruta todos los
 * eventos de la misma comuna siempre a la misma particion, lo que permite
 * paralelizar los avisos sin desordenar la comuna.
 *
 * El mismo DTO existe en {@code wishlist-service} (paquete
 * {@code cl.inmobiliarias.wishlist.dto}) para deserializar del otro lado.
 * El JsonSerializer de Spring incluye el tipo Java en un header, por eso
 * ambos servicios declaran {@code spring.json.trusted.packages} con su
 * propio paquete.
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
