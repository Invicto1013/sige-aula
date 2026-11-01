# Aula Virtual de SIGE para Koyeb (plan gratuito: 512 MB de RAM y 0,1 vCPU).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S aula && adduser -S aula -G aula
WORKDIR /app
COPY --from=build /app/target/sige-aula.jar /app/sige-aula.jar
USER aula
EXPOSE 8000
# Memoria acotada para 512 MB; SerialGC y C1 reducen el uso de CPU en una instancia de 0,1 vCPU.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError"
CMD ["java", "-jar", "/app/sige-aula.jar"]
