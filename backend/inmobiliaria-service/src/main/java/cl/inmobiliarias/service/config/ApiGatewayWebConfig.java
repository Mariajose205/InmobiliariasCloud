package cl.inmobiliarias.service.config;

import cl.inmobiliarias.service.security.IdentidadInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registro del interceptor de identidad.
 *
 * No se registra ningun CORS aqui a proposito. El unico navegador que habla
 * con este microservicio lo hace a traves de API Gateway, y es API Gateway quien
 * responde el preflight y anade las cabeceras Access-Control-*. Si este
 * servicio volviera a declarar CORS, existirian dos capas competeciendo por las
 * cabeceras CORS y cualquier desajuste se manifestaria como errores confusos en
 * el navegador, difíciles de depurar desde aqui.
 */
@Configuration
public class ApiGatewayWebConfig implements WebMvcConfigurer {

    private final IdentidadInterceptor identidadInterceptor;

    public ApiGatewayWebConfig(IdentidadInterceptor identidadInterceptor) {
        this.identidadInterceptor = identidadInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(identidadInterceptor)
                .addPathPatterns("/api/propiedades/**")
                .addPathPatterns("/api/auditoria/**")
                .addPathPatterns("/api/me")
                .addPathPatterns("/api/informes/**")
                .addPathPatterns("/api/admin/**");
    }
}
