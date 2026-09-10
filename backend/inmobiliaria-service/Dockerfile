# Stage 1: compila el microservicio con Maven + JDK 17
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY src ./src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
RUN mvn -q -B -DskipTests package

# Stage 2: imagen de ejecucion en JRE 17
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/inmobiliaria-service-1.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]