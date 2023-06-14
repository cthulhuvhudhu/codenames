# Use an appropriate base image with Java and Gradle installed
FROM eclipse-temurin:17.0.7_7-jre
g
# Set the working directory inside the container
WORKDIR /app

# Copy the necessary files to the container
COPY build/libs/codenames-0.0.1-SNAPSHOT.jar /app/app.jar

# Expose the port that the application will listen on
EXPOSE 8080

# Set the command to run the application when the container starts
CMD ["java", "-jar", "/app/app.jar"]
