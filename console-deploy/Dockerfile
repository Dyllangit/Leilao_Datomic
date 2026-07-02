FROM eclipse-temurin:11-jre-jammy

WORKDIR /app
COPY . /app/
RUN chmod +x /app/bin/transactor /app/bin/console /app/bin/classpath /app/entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]
