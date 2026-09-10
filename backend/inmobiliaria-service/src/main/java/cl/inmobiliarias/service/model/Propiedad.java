package cl.inmobiliarias.service.model;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "propiedades")
public class Propiedad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El titulo es obligatorio")
    @Column(nullable = false)
    private String titulo;

    @NotBlank(message = "La categoria es obligatoria")
    private String categoria;

    @NotBlank(message = "El tipo de operacion es obligatorio")
    private String tipoOperacion;

    @NotNull(message = "El precio es obligatorio")
    @Min(value = 0, message = "El precio no puede ser negativo")
    private Long precio;

    private String tipoPrecio;

    private Long gastosComunes;

    @NotBlank(message = "La ubicacion es obligatoria")
    private String ubicacion;

    private Integer piezas;
    private Integer banos;
    private Integer estacionamiento;
    private Integer bodega;

    private String videoUrl;

    @ElementCollection
    @Column(length = 1000)
    private List<String> imagenes = new ArrayList<>();

    public Propiedad() {
    }

    public Propiedad(String titulo, String categoria, String tipoOperacion, Long precio,
                     String tipoPrecio, Long gastosComunes, String ubicacion,
                     Integer piezas, Integer banos, Integer estacionamiento,
                     Integer bodega, String videoUrl, List<String> imagenes) {
        this.titulo = titulo;
        this.categoria = categoria;
        this.tipoOperacion = tipoOperacion;
        this.precio = precio;
        this.tipoPrecio = tipoPrecio;
        this.gastosComunes = gastosComunes;
        this.ubicacion = ubicacion;
        this.piezas = piezas;
        this.banos = banos;
        this.estacionamiento = estacionamiento;
        this.bodega = bodega;
        this.videoUrl = videoUrl;
        this.imagenes = imagenes != null ? imagenes : new ArrayList<>();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getTipoOperacion() {
        return tipoOperacion;
    }

    public void setTipoOperacion(String tipoOperacion) {
        this.tipoOperacion = tipoOperacion;
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

    public Long getGastosComunes() {
        return gastosComunes;
    }

    public void setGastosComunes(Long gastosComunes) {
        this.gastosComunes = gastosComunes;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }

    public Integer getPiezas() {
        return piezas;
    }

    public void setPiezas(Integer piezas) {
        this.piezas = piezas;
    }

    public Integer getBanos() {
        return banos;
    }

    public void setBanos(Integer banos) {
        this.banos = banos;
    }

    public Integer getEstacionamiento() {
        return estacionamiento;
    }

    public void setEstacionamiento(Integer estacionamiento) {
        this.estacionamiento = estacionamiento;
    }

    public Integer getBodega() {
        return bodega;
    }

    public void setBodega(Integer bodega) {
        this.bodega = bodega;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public List<String> getImagenes() {
        return imagenes;
    }

    public void setImagenes(List<String> imagenes) {
        this.imagenes = imagenes;
    }
}
