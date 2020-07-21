FROM openjdk

WORKDIR /app
COPY rafael-twitter-streamer-app/build/libs/rafael-twitter-streamer-app.jar rafael-twitter-streamer-app.jar
CMD ["java", "-jar", "rafael-twitter-streamer-app.jar", "--spring.config.additional-location=file:/config/secrets/twitter-secrets.yml"]