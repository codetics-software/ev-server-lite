FROM maven:3.9-eclipse-temurin-25 AS build

COPY security /opt/security
RUN /opt/security/distrust-emudhra.sh /opt/security/emudhra-blocked.certs

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre

COPY security /opt/security
RUN /opt/security/distrust-emudhra.sh /opt/security/emudhra-blocked.certs

WORKDIR /app

COPY --from=build /app/target/ev-server-0.0.1-SNAPSHOT.jar app.jar

ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:ActiveProcessorCount=1"

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "app.jar"]
