FROM eclipse-temurin:25-jdk-alpine AS assembler
WORKDIR /assembler

ARG JAR_FILE=build/libs/*.jar

COPY ${JAR_FILE} application.jar

RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /runtime

COPY --from=assembler /assembler/extracted/dependencies/ ./
COPY --from=assembler /assembler/extracted/spring-boot-loader/ ./
COPY --from=assembler /assembler/extracted/snapshot-dependencies/ ./
COPY --from=assembler /assembler/extracted/application/ ./

RUN java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar application.jar

ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-jar", "application.jar"]