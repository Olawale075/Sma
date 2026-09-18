# Use Maven image to build the application
FROM maven:3.9.5-eclipse-temurin-17 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src

# Add better error handling and output
RUN mvn clean package -DskipTests && \
    ls -la target/ && \
    test -f target/sms-0.0.1-SNAPSHOT.jar || (echo "JAR file not found!" && exit 1)

# Use a smaller image to run the app
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copy only the executable jar
COPY --from=builder /app/target/sms-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]