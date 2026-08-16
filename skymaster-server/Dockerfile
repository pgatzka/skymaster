FROM eclipse-temurin:25-jdk-alpine@sha256:5ecfde8e5ecde5954ea3721155b345ef56c1d579b940c761318ad4c05959a151 AS assembler
WORKDIR /assembler

ARG JAR_FILE=build/libs/skymaster-server-*.jar

COPY ${JAR_FILE} application.jar

RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0 AS runtime
WORKDIR /runtime

ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError -XX:+UseParallelGC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/application/heapdump.hprof"

RUN addgroup -S application && adduser -S -G application application && chown application:application /runtime

COPY --from=assembler --chown=application:application /assembler/extracted/dependencies/ ./
COPY --from=assembler --chown=application:application /assembler/extracted/spring-boot-loader/ ./
COPY --from=assembler --chown=application:application /assembler/extracted/snapshot-dependencies/ ./
COPY --from=assembler --chown=application:application /assembler/extracted/application/ ./

USER application

RUN java -XX:AOTCacheOutput=application.aot -Dspring.context.exit=onRefresh -Dspring.profiles.active=aot -jar application.jar

ARG REVISION=unknown
ARG VERSION=unknown

LABEL org.opencontainers.image.source="https://github.com/pgatzka/skymaster" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.version="${VERSION}"

ENTRYPOINT ["java", "-XX:AOTCache=application.aot", "-jar", "application.jar"]