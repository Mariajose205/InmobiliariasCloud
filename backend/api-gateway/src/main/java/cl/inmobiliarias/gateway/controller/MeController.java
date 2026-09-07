package cl.inmobiliarias.gateway.controller;

import cl.inmobiliarias.gateway.security.AzureAutorizacion;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint que devuelve la identidad y el rol del usuario autenticado con el
 * ACCESS TOKEN de nuestra API. Solo acepta tokens emitidos por Entra ID cuya
 * audiencia y scope correspondan a esta API (api://.../access_as_user).
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AzureAutorizacion azureAutorizacion;

    public MeController(AzureAutorizacion azureAutorizacion) {
        this.azureAutorizacion = azureAutorizacion;
    }

    @GetMapping
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Se requiere un access token valido."));
        }

        AzureAutorizacion.Resultado resultado = azureAutorizacion.evaluar(authorization.substring(7));
        if (resultado == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Token invalido, expirado o no es un access token de esta API."));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", resultado.usuario().email());
        body.put("name", resultado.usuario().displayName());
        body.put("objectId", resultado.usuario().objectId());
        body.put("rol", resultado.rol());
        body.put("isAdmin", AzureAutorizacion.ROL_ADMIN.equals(resultado.rol()));
        body.put("isCorredor", AzureAutorizacion.ROL_CORREDOR.equals(resultado.rol()));
        return ResponseEntity.ok(body);
    }
}