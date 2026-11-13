package cl.inmobiliarias.wishlist.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Novedad de una propiedad nueva para un usuario.
 *
 * Cuando se publica una propiedad nueva en una comuna que el usuario sigue
 * (porque guardo un favorito en esa comuna), PropiedadNuevaNotificador guarda
 * una fila aqui por cada usuario afectado, con resumenId = null (pendiente).
 * Las novedades pendientes se agrupan cada `app.notificaciones.umbral`
 * propiedades o en el barrido diario: al formarse el grupo se crea un
 * ResumenNovedades, se enlazan estas filas con su resumenId y se envia UN
 * solo correo. El frontend consulta GET /api/wishlist/novedades y muestra
 * una alerta por resumen, no una por propiedad.
 */
@Entity
@Table(name = "wishlist_novedades",
        uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_email", "propiedad_id"}))
public class Novedad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_email", nullable = false)
    private String usuarioEmail;

    @Column(name = "usuario_oid")
    private String usuarioOid;

    @Column(nullable = false)
    private String comuna;

    @Column(name = "propiedad_id", nullable = false)
    private Long propiedadId;

    @Column(nullable = false)
    private String titulo;

    private Long precio;

    private String tipoPrecio;

    private String ubicacion;

    private String mensaje;

    /**
     * Resumen (digest) al que pertenece esta novedad.
     *
     * null = pendiente: aun no se ha enviado ningun correo con esta propiedad.
     * Con valor = ya forma parte de un resumen enviado (o listo para mostrarse
     * agrupado en Home).
     */
    @Column(name = "resumen_id")
    private Long resumenId;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    public Novedad() {
    }

    public Novedad(String usuarioEmail, String usuarioOid, String comuna, Long propiedadId,
                   String titulo, Long precio, String tipoPrecio, String ubicacion, String mensaje) {
        this.usuarioEmail = usuarioEmail;
        this.usuarioOid = usuarioOid;
        this.comuna = comuna;
        this.propiedadId = propiedadId;
        this.titulo = titulo;
        this.precio = precio;
        this.tipoPrecio = tipoPrecio;
        this.ubicacion = ubicacion;
        this.mensaje = mensaje;
    }

    @PrePersist
    public void prePersist() {
        if (fechaCreacion == null) {
            fechaCreacion = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsuarioEmail() {
        return usuarioEmail;
    }

    public void setUsuarioEmail(String usuarioEmail) {
        this.usuarioEmail = usuarioEmail;
    }

    public String getUsuarioOid() {
        return usuarioOid;
    }

    public void setUsuarioOid(String usuarioOid) {
        this.usuarioOid = usuarioOid;
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

    public Long getResumenId() {
        return resumenId;
    }

    public void setResumenId(Long resumenId) {
        this.resumenId = resumenId;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
