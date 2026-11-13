package cl.inmobiliarias.wishlist.dto;

/**
 * Una propiedad dentro de un resumen de notificaciones.
 *
 * Viaja en el evento Kafka y se usa para armar el correo y las novedades
 * agrupadas de la pagina principal.
 */
public class NotificacionItem {

    private Long propiedadId;

    private String titulo;

    private String comuna;

    private Long precio;

    private String tipoPrecio;

    private String ubicacion;

    public NotificacionItem() {
    }

    public NotificacionItem(Long propiedadId, String titulo, String comuna,
                            Long precio, String tipoPrecio, String ubicacion) {
        this.propiedadId = propiedadId;
        this.titulo = titulo;
        this.comuna = comuna;
        this.precio = precio;
        this.tipoPrecio = tipoPrecio;
        this.ubicacion = ubicacion;
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

    public String getComuna() {
        return comuna;
    }

    public void setComuna(String comuna) {
        this.comuna = comuna;
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
