package cl.inmobiliarias.wishlist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuracion del resumen (digest) de novedades.
 *
 * Se lee de app.notificaciones en application.yml y se puede sobreescribir con
 * variables de entorno en docker-compose (NOTIF_UMBRAL, NOTIF_CRON, NOTIF_ZONA).
 */
@Component
@ConfigurationProperties(prefix = "app.notificaciones")
public class NotificacionesProperties {

    /**
     * Cantidad de propiedades pendientes que dispara el resumen inmediato.
     * Con 6 (valor por defecto) el usuario recibe 1 correo cada 6 propiedades
     * en vez de 6 correos.
     */
    private int umbral = 6;

    /** CRON del barrido diario que envia lo que quedo pendiente (1..5). */
    private String cronResumen = "0 0 20 * * *";

    /** Zona horaria del CRON. */
    private String zona = "America/Santiago";

    public int getUmbral() {
        return umbral;
    }

    public void setUmbral(int umbral) {
        if (umbral < 1) {
            throw new IllegalArgumentException("app.notificaciones.umbral debe ser >= 1");
        }
        this.umbral = umbral;
    }

    public String getCronResumen() {
        return cronResumen;
    }

    public void setCronResumen(String cronResumen) {
        this.cronResumen = cronResumen;
    }

    public String getZona() {
        return zona;
    }

    public void setZona(String zona) {
        this.zona = zona;
    }
}
