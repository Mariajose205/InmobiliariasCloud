package cl.inmobiliarias.wishlist.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Resumen (digest) de novedades de un usuario.
 *
 * En vez de enviar un correo por cada propiedad publicada, el sistema acumula
 * las novedades pendientes en {@link Novedad} (con resumenId = null) y, cuando
 * el usuario junta el umbral configurado (6 por defecto) o cuando corre el
 * resumen diario, se crea UNA fila aqui y se envia UN solo correo con todas
 * las propiedades.
 *
 * Las Novedad que pertenecen al resumen quedan marcadas con su resumenId.
 * El frontend lista los resumenes (una entrada "6 nuevas propiedades en ...")
 * en vez de una entrada por propiedad.
 */
@Entity
@Table(name = "wishlist_novedades_resumen")
public class ResumenNovedades {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_email", nullable = false)
    private String usuarioEmail;

    @Column(name = "usuario_oid")
    private String usuarioOid;

    /** Cantidad de propiedades que incluye el resumen. */
    @Column(nullable = false)
    private Integer cantidad;

    /** Comunas del resumen, separadas por coma y sin repetir. */
    @Column(nullable = false, length = 500)
    private String comunas;

    /**
     * Motivo por el que se genero:
     *  - UMBRAL: el usuario acumulo la cantidad configurada de pendientes.
     *  - DIARIO: barrido programado para que nadie quede sin aviso.
     */
    @Column(nullable = false, length = 20)
    private String motivo;

    // true cuando ya se publico el evento que dispara el correo.
    @Column(name = "correo_enviado", nullable = false)
    private boolean correoEnviado;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    public ResumenNovedades() {
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

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public String getComunas() {
        return comunas;
    }

    public void setComunas(String comunas) {
        this.comunas = comunas;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public boolean isCorreoEnviado() {
        return correoEnviado;
    }

    public void setCorreoEnviado(boolean correoEnviado) {
        this.correoEnviado = correoEnviado;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
