FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# Usar imagen Debian slim (NO alpine) para soporte de fuentes en JasperReports
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Instalar fontconfig y fuentes necesarias para generación de PDFs con JasperReports
RUN apt-get update && apt-get install -y --no-install-recommends \
    fontconfig \
    libfreetype6 \
    && fc-cache -fv \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Comando de inicio
ENTRYPOINT ["java", "-jar", "app.jar"]