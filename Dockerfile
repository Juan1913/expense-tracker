# Dockerfile para EasyPanel - Java 21
FROM eclipse-temurin:21-jdk-alpine AS builder

# Instalar Maven
RUN apk add --no-cache maven

WORKDIR /app

# Copiar archivos de configuración
COPY pom.xml .
COPY .mvn/ .mvn/

# Copiar código fuente
COPY src/ src/

# Construir la aplicación
RUN mvn clean package -DskipTests=true -Dmaven.javadoc.skip=true

# Imagen final con Java 21 Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copiar el JAR construido
COPY --from=builder /app/target/*.jar app.jar

# Exponer puerto
EXPOSE 8000

# Variables de entorno para Spring Boot
ENV SPRING_PROFILES_ACTIVE=prod

# Comando de inicio
CMD ["java", "-Dserver.port=${PORT:-8000}", "-jar", "app.jar"]
