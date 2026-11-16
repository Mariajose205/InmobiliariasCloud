package cl.inmobiliarias.wishlist.dto;

/**
 * Copia local del evento que publica inmobiliaria-service en el topic
 * {@code propiedades-eliminadas} cuando un admin o corredor borra una
 * propiedad.
 *
 * Se usa para limpiar los favoritos de todos los usuarios y crear un aviso
 * visible en la pagina ("ya fue vendida o arrendada, se elimino de tus
 * favoritos"). El mapeo entre paquetes lo resuelve
 * {@code spring.json.type.mappings}.
 */
public class PropiedadEliminadaEvent {

    private Long propiedadId;

    private String titulo;

    private String tipoOperacion;

    private String ubicacion;

    private String comuna;

    public PropiedadEliminadaEvent() {
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

    public String getTipoOperacion() {
        return tipoOperacion;
    }

    public void setTipoOperacion(String tipoOperacion) {
        this.tipoOperacion = tipoOperacion;
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
}
