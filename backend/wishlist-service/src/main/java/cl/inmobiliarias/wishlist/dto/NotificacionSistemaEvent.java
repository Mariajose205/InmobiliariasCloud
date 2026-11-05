package cl.inmobiliarias.wishlist.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Notificacion interna (topic notificaciones-sistema).
 *
 * La crea PropiedadNuevaNotificador a partir de PropiedadCreadaEvent y
 * la consume NotificacionDispatchConsumer, que finalmente envia el correo.
 * La clave Kafka es la comuna, para que los correos de una misma comuna
 * salgan ordenados por particion.
 */
public class NotificacionSistemaEvent {

    @NotBlank(message = "El destinatario es obligatorio")
    private String destinatarioEmail;

    private String comuna;

    private Long propiedadId;

    private String titulo;

    private Long precio;

    private String tipoPrecio;

    private String ubicacion;

    private String mensaje;

    public NotificacionSistemaEvent() {
    }

    public String getDestinatarioEmail() {
        return destinatarioEmail;
    }

    public void setDestinatarioEmail(String destinatarioEmail) {
        this.destinatarioEmail = destinatarioEmail;
    }

    public String getComuna() {
        return comuna;
    }

    public void setComuna(String comuna) {
        this.comuna = comuna;
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

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}
