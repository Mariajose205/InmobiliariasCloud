package cl.inmobiliarias.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Proxy del API Gateway: reenvia las peticiones del frontend hacia el
 * microservicio `inmobiliaria-service`, que NO es publico (solo loopback:
 * 127.0.0.1:8081). El gateway es la unica puerta de entrada.
 *
 * El interceptor PropiedadAuthInterceptor se ejecuta antes de estos metodos,
 * garantizando que solo un ADMIN puede crear/actualizar/eliminar.
 */
@RestController
@RequestMapping("/api/propiedades")
public class PropiedadProxyController {

    private final RestTemplate restTemplate;

    @Value("${gateway.inmobiliaria-service-url}")
    private String serviceBaseUrl;

    public PropiedadProxyController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping
    public ResponseEntity<?> listar(@RequestParam(required = false) String categoria) {
        String url = serviceBaseUrl + "/api/propiedades";
        if (categoria != null && !categoria.isBlank()) {
            url += "?categoria=" + categoria;
        }
        return restTemplate.exchange(url, HttpMethod.GET, null, Object.class);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable Long id) {
        return restTemplate.exchange(serviceBaseUrl + "/api/propiedades/" + id,
                HttpMethod.GET, null, Object.class);
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(serviceBaseUrl + "/api/propiedades",
                HttpMethod.POST, entity, Object.class);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(serviceBaseUrl + "/api/propiedades/" + id,
                HttpMethod.PUT, entity, Object.class);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        return restTemplate.exchange(serviceBaseUrl + "/api/propiedades/" + id,
                HttpMethod.DELETE, null, Object.class);
    }
}
