# Use Java 17
FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy all files
COPY . .

# Build the app
RUN ./mvnw clean package -DskipTests

# Run the app
CMD ["java", "-jar", "target/*.jar"]