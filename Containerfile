FROM docker.io/eclipse-temurin:25-jre-ubi10-minimal
COPY target/mention-bot-1.0-SNAPSHOT.jar /app/app.jar
WORKDIR /app
ENTRYPOINT ["java", "-jar", "app.jar"]
