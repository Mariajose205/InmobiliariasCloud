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
 * Aviso personal que se muestra en la pagina del cliente cuando una propiedad
 * que tenia en favoritos fue eliminada del catalogo (vendida o arrendada).
 *
 * Se crea al consumir el topic {@code propiedades-eliminadas}: por cada usuario
 * que tenia la propiedad en favoritos se guarda una fila y, en la misma
 * transaccion, se borra el favorito. El frontend los muestra como un mensaje
 * destacado y el usuario puede descartarlo (DELETE).
 *
 * La restriccion unica (usuario, propiedad) evita duplicados si Kafka
 * reentrega el mismo evento.
 */
@Entity
@Table(name = "wishlist_avisos",
        uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_email", "propiedad_id"}))
public class Aviso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_email", nullable = false)
    private String usuarioEmail;

    @Column(name = "usuario_oid")
    private String usuarioOid;

    @Column(name = "propiedad_id", nullable = false)
    private Long propiedadId;

    private String titulo;

    private String comuna;

    /** VENTA, ARRIENDO o el valor que haya enviado el catalogo. */
    @Column(name = "tipo_operacion")
    private String tipoOperacion;

    @Column(nullable = false, length = 500)
    private String mensaje;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    public Aviso() {
    }

    public Aviso(String usuarioEmail, String usuarioOid, Long propiedadId, String titulo,
                 String comuna, String tipoOperacion, String mensaje) {
        this.usuarioEmail = usuarioEmail;
        this.usuarioOid = usuarioOid;
        this.propiedadId = propiedadId;
        this.titulo = titulo;
        this.comuna = comuna;
        this.tipoOperacion = tipoOperacion;
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

    public String getTipoOperacion() {
        return tipoOperacion;
    }

    public void setTipoOperacion(String tipoOperacion) {
        this.tipoOperacion = tipoOperacion;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
