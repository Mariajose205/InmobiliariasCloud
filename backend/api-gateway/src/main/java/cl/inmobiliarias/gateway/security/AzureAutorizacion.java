package cl.inmobiliarias.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resuelve el rol de un usuario autenticado con Azure Entra ID.
 *
 * Roles:
 *  - ADMIN: administra el catalogo (crear, editar y eliminar).
 *  - CORREDOR: solo publicar (crear) y eliminar; NO puede editar.
 *  - PUBLIC: token valido de un usuario sin rol.
 *
 * La identificacion se hace con el email O el objectId (oid) del access token.
 */
@Component
public class AzureAutorizacion {

    public static final String ROL_ADMIN = "ADMIN";
    public static final String ROL_CORREDOR = "CORREDOR";
    public static final String ROL_PUBLIC = "PUBLIC";

    public record Resultado(AzureTokenValidator.AzureUser usuario, String rol) {
    }

    private final AzureTokenValidator azureTokenValidator;
    private final String adminEmail;
    private final String adminObjectId;
    private final String corredorEmail;
    private final String corredorObjectId;

    public AzureAutorizacion(AzureTokenValidator azureTokenValidator,
                             @Value("${azure.admin-email:}") String adminEmail,
                             @Value("${azure.admin-object-id:}") String adminObjectId,
                             @Value("${azure.corredor-email:}") String corredorEmail,
                             @Value("${azure.corredor-object-id:}") String corredorObjectId) {
        this.azureTokenValidator = azureTokenValidator;
        this.adminEmail = adminEmail;
        this.adminObjectId = adminObjectId;
        this.corredorEmail = corredorEmail;
        this.corredorObjectId = corredorObjectId;
    }

    /**
     * Valida el token y devuelve el rol del usuario. Devuelve {@code null} si
     * el token no es valido.
     */
    public Resultado evaluar(String bearerToken) {
        AzureTokenValidator.AzureUser usuario = azureTokenValidator.validate(bearerToken);
        if (usuario == null || !usuario.valid()) {
            return null;
        }
        if (coincide(adminEmail, usuario.email()) || coincide(adminObjectId, usuario.objectId())) {
            return new Resultado(usuario, ROL_ADMIN);
        }
        if (coincide(corredorEmail, usuario.email()) || coincide(corredorObjectId, usuario.objectId())) {
            return new Resultado(usuario, ROL_CORREDOR);
        }
        return new Resultado(usuario, ROL_PUBLIC);
    }

    private boolean coincide(String config, String claim) {
        return config != null && !config.isBlank()
                && claim != null && config.equalsIgnoreCase(claim);
    }
}