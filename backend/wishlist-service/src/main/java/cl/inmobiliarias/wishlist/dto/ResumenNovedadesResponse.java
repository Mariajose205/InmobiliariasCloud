package cl.inmobiliarias.wishlist.dto;

import java.time.Instant;
import java.util.List;

/**
 * Resumen de novedades que consume la pagina principal.
 *
 * Reemplaza a la lista de novedades individuales: el frontend muestra UNA
 * entrada por resumen ("6 nuevas propiedades en Quillota") y puede desplegar
 * el detalle de cada propiedad.
 */
public class ResumenNovedadesResponse {

    private Long id;

    private Instant fechaCreacion;

    private Integer cantidad;

    /** Comunas que cubre el resumen (sin repetir). */
    private List<String> comunas;

    /** Texto listo para mostrar, p.ej. "6 nuevas propiedades en Quillota". */
    private String titulo;

    private List<ItemNovedadResponse> items;

    public ResumenNovedadesResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
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

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public List<ItemNovedadResponse> getItems() {
        return items;
    }

    public void setItems(List<ItemNovedadResponse> items) {
        this.items = items;
    }

    /** Detalle de una propiedad dentro del resumen. */
    public static class ItemNovedadResponse {

        private Long propiedadId;

        private String titulo;

        private String comuna;

        private Long precio;

        private String tipoPrecio;

        private String ubicacion;

        public ItemNovedadResponse() {
        }

        public ItemNovedadResponse(Long propiedadId, String titulo, String comuna,
                                   Long precio, String tipoPrecio, String ubicacion) {
            this.propiedadId = propiedadId;
            this.titulo = titulo;
            this.comuna = comuna;
            this.precio = precio;
            this.tipoPrecio = tipoPrecio;
            this.ubicacion = ubicacion;
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
    }
}
