package cl.inmobiliarias.service.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuracion de autorizacion del microservicio.
 *
 * La identidad del usuario ya NO se deduce del token aqui: API Gateway valida
 * el access token de Azure Entra ID con su autorizador JWT nativo y reenvia la
 * identidad en las cabeceras `X-User`, `X-User-Oid` y `X-User-Name`, que
 * sobrescribe cualquier valor que envie el cliente.
 *
 * Lo que si sigue haciendo el microservicio es resolver el ROL a partir de esa
 * identidad y aplicar la matriz de permisos, porque API Gateway no conoce los
 * roles del negocio.
 *
 * @param autorizacionRequerida cuando es true, una peticion que llega sin
 *                               identidad es rechazada. Debe estar activo en
 *                               produccion para impedir que alguien salte el
 *                               API Gateway y llame al microservicio directo.
 * @param adminEmail             correo del administrador.
 * @param adminOid               objectId (oid) del administrador.
 * @param corredorEmail          correo del corredor.
 * @param corredorOid            objectId (oid) del corredor.
 */
@Component
@ConfigurationProperties(prefix = "seguridad")
public class SeguridadProperties {

    private boolean autorizacionRequerida = false;
    private String adminEmail = "";
    private String adminOid = "";
    private String corredorEmail = "";
    private String corredorOid = "";

    public boolean isAutorizacionRequerida() {
        return autorizacionRequerida;
    }

    public void setAutorizacionRequerida(boolean autorizacionRequerida) {
        this.autorizacionRequerida = autorizacionRequerida;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getAdminOid() {
        return adminOid;
    }

    public void setAdminOid(String adminOid) {
        this.adminOid = adminOid;
    }

    public String getCorredorEmail() {
        return corredorEmail;
    }

    public void setCorredorEmail(String corredorEmail) {
        this.corredorEmail = corredorEmail;
    }

    public String getCorredorOid() {
        return corredorOid;
    }

    public void setCorredorOid(String corredorOid) {
        this.corredorOid = corredorOid;
    }
}
