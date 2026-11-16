package cl.inmobiliarias.service.dto;

/**
 * Evento que se publica en el topic {@code propiedades-eliminadas} cuando un
 * administrador o un corredor elimina una propiedad del catalogo.
 *
 * El wishlist-service lo consume para sacar la propiedad de los favoritos de
 * todos los usuarios y dejarles un aviso visible en su pagina ("ya fue vendida
 * o arrendada, se elimino de tus favoritos").
 *
 * La CLAVE Kafka es la comuna canonica (o "OTROS"), igual que en
 * {@link PropiedadCreadaEvent}, para conservar el orden por comuna.
 *
 * El mismo DTO existe en {@code wishlist-service} (paquete
 * {@code cl.inmobiliarias.wishlist.dto}). El JsonSerializer agrega el tipo Java
 * en un header; ambos servicios lo traducen con {@code spring.json.type.mappings}.
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
