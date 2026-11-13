package cl.inmobiliarias.wishlist.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Notificacion interna (topic notificaciones-sistema).
 *
 * La crea ResumenNovedadesService a partir de las novedades acumuladas y la
 * consume NotificacionDispatchConsumer, que envia UN correo con el detalle.
 * La clave Kafka es el correo del destinatario, para que los resumenes de un
 * mismo usuario salgan ordenados en la misma particion.
 *
 * Los campos individuales (comuna, titulo, precio...) se conservan para poder
 * procesar mensajes de versiones anteriores; el formato nuevo usa "items".
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

    /** Formato nuevo: cantidad de propiedades del resumen. */
    private Integer cantidad;

    /** Formato nuevo: comunas cubiertas por el resumen. */
    private List<String> comunas;

    /** Formato nuevo: detalle de las propiedades incluidas. */
    private List<NotificacionItem> items;

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

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public List<String> getComunas() {
        return comunas;
    }

    public void setComunas(List<String> comunas) {
        this.comunas = comunas;
    }

    public List<NotificacionItem> getItems() {
        return items;
    }

    public void setItems(List<NotificacionItem> items) {
        this.items = items;
    }
}
