FROM maven:3.8.7-eclipse-temurin-17-alpine AS builder
WORKDIR /app
COPY pom.xml .
# Override compiler version to match JDK 17
RUN sed -i 's/<java.version>21<\/java.version>/<java.version>17<\/java.version>/' pom.xml && \
    sed -i 's/<maven.compiler.source>21<\/maven.compiler.source>/<maven.compiler.source>17<\/maven.compiler.source>/' pom.xml && \
    sed -i 's/<maven.compiler.target>21<\/maven.compiler.target>/<maven.compiler.target>17<\/maven.compiler.target>/' pom.xml && \
    sed -i 's/<maven.compiler.release>21<\/maven.compiler.release>/<maven.compiler.release>17<\/maven.compiler.release>/' pom.xml
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM maven:3.8.7-eclipse-temurin-17-alpine
WORKDIR /app
COPY --from=builder /app/target/similar-products-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
