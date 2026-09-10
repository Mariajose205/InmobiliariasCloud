package cl.inmobiliarias.service.config;

import cl.inmobiliarias.service.model.Auditoria;
import cl.inmobiliarias.service.model.Propiedad;
import cl.inmobiliarias.service.repository.AuditoriaRepository;
import cl.inmobiliarias.service.repository.PropiedadRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private final PropiedadRepository repository;
    private final AuditoriaRepository auditoriaRepository;

    public DataSeeder(PropiedadRepository repository, AuditoriaRepository auditoriaRepository) {
        this.repository = repository;
        this.auditoriaRepository = auditoriaRepository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }

        repository.saveAll(List.of(
                new Propiedad(
                        "Hermosa Casa en Las Condes", "Casa", "Venta", 150000000L,
                        "/ Valor Total", 50000L, "Las Condes, Santiago",
                        4, 3, 2, 1, "", List.of("imagenes/Habitacion_fondo.png")),
                new Propiedad(
                        "Departamento Moderno Centro", "Departamento", "Venta", 85000000L,
                        "/ Valor Total", 30000L, "Santiago Centro",
                        2, 1, 1, 0, "", List.of("imagenes/Logo_Principal.jpeg")),
                new Propiedad(
                        "Terreno en La Dehesa", "Terreno", "Venta", 200000000L,
                        "/ Valor Total", 0L, "La Dehesa",
                        0, 0, 0, 0, "", List.of("imagenes/Fondo_negro.png"))
        ));

        List<Auditoria> demoAuditoria = List.of(
                new Auditoria("CREAR", "Departamento Moderno Centro",
                        "administrador@inmobiliariaduoc.onmicrosoft.com", "ADMIN"),
                new Auditoria("EDITAR", "Hermosa Casa en Las Condes",
                        "administrador@inmobiliariaduoc.onmicrosoft.com", "ADMIN"),
                new Auditoria("CREAR", "Terreno en La Dehesa",
                        "Corredor@InmobiliariaDuoc.onmicrosoft.com", "CORREDOR")
        );
        demoAuditoria.get(0).setFecha(LocalDateTime.now().minusHours(3));
        demoAuditoria.get(1).setFecha(LocalDateTime.now().minusDays(1));
        demoAuditoria.get(2).setFecha(LocalDateTime.now().minusDays(2));
        auditoriaRepository.saveAll(demoAuditoria);
    }
}
