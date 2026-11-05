package cl.inmobiliarias.service.config.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de los topicos Kafka del flujo "nueva propiedad -> aviso por comuna".
 * Leidos desde {@code app.kafka.topics} en application.yml.
 */
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaProperties {

    private Topics topics = new Topics();

    public Topics getTopics() {
        return topics;
    }

    public void setTopics(Topics topics) {
        this.topics = topics;
    }

    public static class Topics {
        private String propiedadesNuevas = "propiedades-nuevas";
        private String notificacionesSistema = "notificaciones-sistema";
        private String propiedadesNuevasDlq = "propiedades-nuevas.dlq";
        private String notificacionesSistemaDlq = "notificaciones-sistema.dlq";

        public String getPropiedadesNuevas() {
            return propiedadesNuevas;
        }

        public void setPropiedadesNuevas(String propiedadesNuevas) {
            this.propiedadesNuevas = propiedadesNuevas;
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

        public String getNotificacionesSistemaDlq() {
            return notificacionesSistemaDlq;
        }

        public void setNotificacionesSistemaDlq(String notificacionesSistemaDlq) {
            this.notificacionesSistemaDlq = notificacionesSistemaDlq;
        }
    }
}
