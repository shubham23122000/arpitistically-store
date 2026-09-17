FROM eclipse-temurin:21-jdk

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven

# Copy project files
COPY . .

# Build the app
RUN mvn clean package -DskipTests

# Run the app
CMD sh -c "java -jar target/*.jar"