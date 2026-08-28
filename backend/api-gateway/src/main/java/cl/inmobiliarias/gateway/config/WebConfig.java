package cl.inmobiliarias.gateway.config;

import cl.inmobiliarias.gateway.security.PropiedadAuthInterceptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PropiedadAuthInterceptor authInterceptor;

    public WebConfig(PropiedadAuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/propiedades/**")
                .addPathPatterns("/api/auth/**");
    }
}
