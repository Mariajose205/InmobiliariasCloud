package cl.inmobiliarias.wishlist.config.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Nombres de los topicos Kafka del flujo
 * "nueva propiedad -> aviso por comuna".
 *
 * Los valores vienen de app.kafka.topics en application.yml y tienen valores
 * por defecto, para que el servicio arranque aunque falte la configuracion.
 */
@Component
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaProperties {

    private Topics topics = new Topics();

    public Topics getTopics() {
        return topics;
    }

    public void setTopics(Topics topics) {
        this.topics = topics;
    }

    /**
     * Los topicos del flujo: las altas y las bajas de propiedades, las
     * notificaciones por correo, y la DLQ de cada consumidor.
     */
    public static class Topics {

        private String propiedadesNuevas = "propiedades-nuevas";
        private String propiedadesEliminadas = "propiedades-eliminadas";
        private String notificacionesSistema = "notificaciones-sistema";
        private String propiedadesNuevasDlq = "propiedades-nuevas.dlq";
        private String propiedadesEliminadasDlq = "propiedades-eliminadas.dlq";
        private String notificacionesSistemaDlq = "notificaciones-sistema.dlq";

        public String getPropiedadesNuevas() {
            return propiedadesNuevas;
        }

        public void setPropiedadesNuevas(String propiedadesNuevas) {
            this.propiedadesNuevas = propiedadesNuevas;
        }

        public String getPropiedadesEliminadas() {
            return propiedadesEliminadas;
        }

        public void setPropiedadesEliminadas(String propiedadesEliminadas) {
            this.propiedadesEliminadas = propiedadesEliminadas;
        }

        public String getNotificacionesSistema() {
            return notificacionesSistema;
        }

        public void setNotificacionesSistema(String notificacionesSistema) {
            this.notificacionesSistema = notificacionesSistema;
        }

        public String getPropiedadesNuevasDlq() {
            return propiedadesNuevasDlq;
        }

        public void setPropiedadesNuevasDlq(String propiedadesNuevasDlq) {
            this.propiedadesNuevasDlq = propiedadesNuevasDlq;
        }

        public String getPropiedadesEliminadasDlq() {
            return propiedadesEliminadasDlq;
        }

        public void setPropiedadesEliminadasDlq(String propiedadesEliminadasDlq) {
            this.propiedadesEliminadasDlq = propiedadesEliminadasDlq;
        }

        public String getNotificacionesSistemaDlq() {
            return notificacionesSistemaDlq;
        }

        public void setNotificacionesSistemaDlq(String notificacionesSistemaDlq) {
            this.notificacionesSistemaDlq = notificacionesSistemaDlq;
        }
    }
}
