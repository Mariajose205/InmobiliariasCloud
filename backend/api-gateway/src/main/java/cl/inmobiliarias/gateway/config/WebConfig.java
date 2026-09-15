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
                .addPathPatterns("/api/auth/**")
                .addPathPatterns("/api/auditoria/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Solo se permite el origen de produccion. El frontend y el gateway se
        // sirven detras del mismo dominio DuckDNS (nginx proxya /api -> gateway),
        // por lo que en la practica las peticiones son mismo-origen.
        registry.addMapping("/api/**")
                .allowedOrigins("https://inmobiliariasduoc.duckdns.org")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true);
    }
}
