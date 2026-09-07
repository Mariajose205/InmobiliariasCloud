package cl.inmobiliarias.gateway.config;

import cl.inmobiliarias.gateway.security.PropiedadAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PropiedadAuthInterceptor authInterceptor;

    public WebConfig(PropiedadAuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/propiedades/**")
                .addPathPatterns("/api/auth/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Permite que la SPA en desarrollo (Vite, puerto 5173) llame al gateway
        // directamente (http://localhost:8080/api/...). Con el proxy de Vite
        // las peticiones son mismo-origen, pero el CORS tambien soporta el uso
        // de una base URL absoluta.
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true);
    }
}
