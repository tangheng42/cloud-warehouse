FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /home/app /app/logs && chown -R 10001:10001 /home/app /app
ENV HOME=/home/app
ENV JAVA_TOOL_OPTIONS=-Duser.home=/home/app
ARG JAR_FILE
COPY ${JAR_FILE} /app/application.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
