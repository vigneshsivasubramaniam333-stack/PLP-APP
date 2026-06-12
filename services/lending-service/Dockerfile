FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY mvnw .
RUN chmod +x mvnw
COPY .mvn .mvn
COPY pom.xml .
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/discovery-service/pom.xml services/discovery-service/pom.xml
COPY services/iam-service/pom.xml services/iam-service/pom.xml
COPY services/integration-service/pom.xml services/integration-service/pom.xml
COPY services/lending-service/pom.xml services/lending-service/pom.xml
COPY services/notification-service/pom.xml services/notification-service/pom.xml
COPY services/program-service/pom.xml services/program-service/pom.xml
COPY services/report-service/pom.xml services/report-service/pom.xml
COPY services/lending-service/src services/lending-service/src
RUN ./mvnw dependency:go-offline -q -pl services/lending-service
RUN ./mvnw package -DskipTests -q -pl services/lending-service
RUN test -f services/lending-service/target/lending-service-*.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S plp && adduser -S plp -G plp
COPY --from=builder --chown=plp:plp /build/services/lending-service/target/lending-service-*.jar app.jar
USER plp
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
