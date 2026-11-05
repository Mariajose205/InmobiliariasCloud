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
 * Favorito de un usuario.
 *
 * Guardamos una copia minima de los datos de la propiedad (titulo, precio,
 * ubicacion, comuna) para que la wishlist se pueda listar sin llamar al
 * inmobiliaria-service en cada peticion.
 */
@Entity
@Table(name = "wishlist_favoritos", uniqueConstraints = @UniqueConstraint({"usuario_email", "propiedad_id"}))
public class Favorito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_email", nullable = false)
    private String usuarioEmail;

    @Column(name = "usuario_oid")
    private String usuarioOid;

    @Column(name = "propiedad_id", nullable = false)
    private Long propiedadId;

    @Column(nullable = false)
    private String titulo;

    private Long precio;

    private String tipoPrecio;

    private String ubicacion;

    /** Comuna canonica extraida de la ubicacion (ver Comunas), puede ser null. */
    private String comuna;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    public Favorito() {
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

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
