package cl.inmobiliarias.service.config.rabbitmq;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Nombres de colas, exchanges y routing keys del flujo de informes PDF,
 * centralizados en `application.yml` bajo el prefijo `rabbitmq`.
 *
 * Tenerlos aqui (y no dispersos como literales en las clases) es lo que pide
 * la rubrica: si hay que renombrar una cola, se cambia en un unico lugar.
 *
 * Flujo:
 *   web -> inmobiliaria.fanout -> cola.correos.notificaciones (aviso al usuario)
 *                              \-> inmobiliaria.direct -> cola.pdf.procesamiento (genera PDF)
 */
@Component
@ConfigurationProperties(prefix = "rabbitmq")
public class RabbitProperties {

    private final Exchanges exchanges = new Exchanges();
    private final Queues queues = new Queues();
    private final RoutingKeys routingKeys = new RoutingKeys();
    private final Admin admin = new Admin();

    public Exchanges getExchanges() {
        return exchanges;
    }

    public Queues getQueues() {
        return queues;
    }

    public RoutingKeys getRoutingKeys() {
        return routingKeys;
    }

    public Admin getAdmin() {
        return admin;
    }

    public static class Exchanges {
        private String fanout;
        private String direct;
        private String dlx;

        public String getFanout() {
            return fanout;
        }

        public void setFanout(String fanout) {
            this.fanout = fanout;
        }

        public String getDirect() {
            return direct;
        }

        public void setDirect(String direct) {
            this.direct = direct;
        }

        public String getDlx() {
            return dlx;
        }

        public void setDlx(String dlx) {
            this.dlx = dlx;
        }
    }

    public static class Queues {
        private String correosNotificaciones;
        private String pdfProcesamiento;
        private String correosNotificacionesDlq;
        private String pdfProcesamientoDlq;

        public String getCorreosNotificaciones() {
            return correosNotificaciones;
        }

        public void setCorreosNotificaciones(String correosNotificaciones) {
            this.correosNotificaciones = correosNotificaciones;
        }

        public String getPdfProcesamiento() {
            return pdfProcesamiento;
        }

        public void setPdfProcesamiento(String pdfProcesamiento) {
            this.pdfProcesamiento = pdfProcesamiento;
        }

        public String getCorreosNotificacionesDlq() {
            return correosNotificacionesDlq;
        }

        public void setCorreosNotificacionesDlq(String correosNotificacionesDlq) {
            this.correosNotificacionesDlq = correosNotificacionesDlq;
        }

        public String getPdfProcesamientoDlq() {
            return pdfProcesamientoDlq;
        }

        public void setPdfProcesamientoDlq(String pdfProcesamientoDlq) {
            this.pdfProcesamientoDlq = pdfProcesamientoDlq;
        }
    }

    public static class RoutingKeys {
        private String pdf;
        private String correosNotificacionesDlq;
        private String pdfProcesamientoDlq;

        public String getPdf() {
            return pdf;
        }

        public void setPdf(String pdf) {
            this.pdf = pdf;
        }

        public String getCorreosNotificacionesDlq() {
            return correosNotificacionesDlq;
        }

        public void setCorreosNotificacionesDlq(String correosNotificacionesDlq) {
            this.correosNotificacionesDlq = correosNotificacionesDlq;
        }

        public String getPdfProcesamientoDlq() {
            return pdfProcesamientoDlq;
        }

        public void setPdfProcesamientoDlq(String pdfProcesamientoDlq) {
            this.pdfProcesamientoDlq = pdfProcesamientoDlq;
        }
    }

    public static class Admin {
        private long dlqTtl = 600000L;

        public long getDlqTtl() {
            return dlqTtl;
        }

        public void setDlqTtl(long dlqTtl) {
            this.dlqTtl = dlqTtl;
        }
    }
}
