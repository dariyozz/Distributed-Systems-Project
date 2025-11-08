# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port for health checks (if needed)
EXPOSE 8081

# Set environment variables
ENV KAFKA_BROKERS=localhost:9092

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
