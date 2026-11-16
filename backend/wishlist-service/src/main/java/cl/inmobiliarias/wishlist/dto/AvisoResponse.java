package cl.inmobiliarias.wishlist.dto;

import java.time.Instant;

/**
 * Aviso que se muestra en la pagina del cliente cuando una propiedad de sus
 * favoritos fue eliminada por un administrador o corredor.
 */
public class AvisoResponse {

    private Long id;

    private Long propiedadId;

    private String titulo;

    private String comuna;

    private String tipoOperacion;

    private String mensaje;

    private Instant fechaCreacion;

    public AvisoResponse() {
    }

    public AvisoResponse(Long id, Long propiedadId, String titulo, String comuna,
                         String tipoOperacion, String mensaje, Instant fechaCreacion) {
        this.id = id;
        this.propiedadId = propiedadId;
        this.titulo = titulo;
        this.comuna = comuna;
        this.tipoOperacion = tipoOperacion;
        this.mensaje = mensaje;
        this.fechaCreacion = fechaCreacion;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
