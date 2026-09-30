FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
# El pom solo cambia cuando cambian las dependencias: se resuelven en una capa aparte para que
# editar codigo no vuelva a descargarlas.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package && cp target/*.jar app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
