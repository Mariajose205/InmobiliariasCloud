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
 * Alerta de un usuario sobre una comuna.
 *
 * Cuando el usuario sigue una comuna, PropiedadNuevaNotificador busca todas
 * las alertas de esa comuna y publica una notificacion por cada usuario.
 */
@Entity
@Table(name = "wishlist_alertas", uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_email", "comuna"}))
public class AlertaComuna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_email", nullable = false)
    private String usuarioEmail;

    @Column(name = "usuario_oid")
    private String usuarioOid;

    /** Comuna canonica (ver Comunas). */
    @Column(nullable = false)
    private String comuna;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    public AlertaComuna() {
    }

    public AlertaComuna(String usuarioEmail, String usuarioOid, String comuna) {
        this.usuarioEmail = usuarioEmail;
        this.usuarioOid = usuarioOid;
        this.comuna = comuna;
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

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
