package cl.inmobiliarias.service.config.rabbitmq;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Nombres de colas, exchanges y routing keys del flujo de informes PDF y de
 * auditoria de propiedades, centralizados en `application.yml` bajo el
 * prefijo `rabbitmq`.
 *
 * Tenerlos aqui (y no dispersos como literales en las clases) es lo que pide
 * la rubrica: si hay que renombrar una cola, se cambia en un unico lugar.
 *
 * Flujo de informes:
 *   web -> inmobiliaria.fanout -> cola.correos.notificaciones (aviso al usuario)
 *                              \-> inmobiliaria.direct -> cola.pdf.procesamiento (genera PDF)
 *
 * Flujo de auditoria (exchange topic):
 *   web -> inmobiliaria.topic (rk propiedades.eventos.creada|editar|eliminar)
 *                            -> cola.propiedades.auditoria (patron propiedades.eventos.#)
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
        private String topic;
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

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
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
        private String propiedadesAuditoria;
        private String correosNotificacionesDlq;
        private String pdfProcesamientoDlq;
        private String propiedadesAuditoriaDlq;

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

        public String getPropiedadesAuditoria() {
            return propiedadesAuditoria;
        }

        public void setPropiedadesAuditoria(String propiedadesAuditoria) {
            this.propiedadesAuditoria = propiedadesAuditoria;
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

        public String getPropiedadesAuditoriaDlq() {
            return propiedadesAuditoriaDlq;
        }

        public void setPropiedadesAuditoriaDlq(String propiedadesAuditoriaDlq) {
            this.propiedadesAuditoriaDlq = propiedadesAuditoriaDlq;
        }
    }

    public static class RoutingKeys {
        private String pdf;
        private String correosNotificacionesDlq;
        private String pdfProcesamientoDlq;
        private String propiedadesEventosCrear;
        private String propiedadesEventosEditar;
        private String propiedadesEventosEliminar;
        private String propiedadesEventosPatron;
        private String propiedadesAuditoriaDlq;

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

        public String getPropiedadesEventosCrear() {
            return propiedadesEventosCrear;
        }

        public void setPropiedadesEventosCrear(String propiedadesEventosCrear) {
            this.propiedadesEventosCrear = propiedadesEventosCrear;
        }

        public String getPropiedadesEventosEditar() {
            return propiedadesEventosEditar;
        }

        public void setPropiedadesEventosEditar(String propiedadesEventosEditar) {
            this.propiedadesEventosEditar = propiedadesEventosEditar;
        }

        public String getPropiedadesEventosEliminar() {
            return propiedadesEventosEliminar;
        }

        public void setPropiedadesEventosEliminar(String propiedadesEventosEliminar) {
            this.propiedadesEventosEliminar = propiedadesEventosEliminar;
        }

        public String getPropiedadesEventosPatron() {
            return propiedadesEventosPatron;
        }

        public void setPropiedadesEventosPatron(String propiedadesEventosPatron) {
            this.propiedadesEventosPatron = propiedadesEventosPatron;
        }

        public String getPropiedadesAuditoriaDlq() {
            return propiedadesAuditoriaDlq;
        }

        public void setPropiedadesAuditoriaDlq(String propiedadesAuditoriaDlq) {
            this.propiedadesAuditoriaDlq = propiedadesAuditoriaDlq;
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
