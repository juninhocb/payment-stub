FROM openjdk:17

WORKDIR /app

COPY target/payment-0.0.1-SNAPSHOT.jar /app/payment.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "payment.jar"]