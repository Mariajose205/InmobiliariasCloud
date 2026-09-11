package cl.inmobiliarias.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * Proxy de la auditoria: solo accesible por el ADMIN (el interceptor
 * {@code PropiedadAuthInterceptor} rechaza cualquier otro rol o visitante).
 * Devuelve el registro de quien y cuando realizo acciones sobre las propiedades.
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaProxyController {

    private final RestTemplate restTemplate;

    @Value("${gateway.inmobiliaria-service-url}")
    private String serviceBaseUrl;

    public AuditoriaProxyController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping
    public ResponseEntity<?> listar() {
        return ProxyRespuestas.responder(restTemplate.exchange(serviceBaseUrl + "/api/auditoria",
                HttpMethod.GET, null, Object.class));
    }
}