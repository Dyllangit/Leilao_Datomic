FROM eclipse-temurin:11-jdk-jammy

WORKDIR /app
COPY vendor ./vendor
COPY resources ./resources
COPY src ./src

RUN javac -encoding UTF-8 -cp "vendor/lib/*:vendor/peer-1.0.7622.jar" -d out src/Bridge.java

ENV BRIDGE_PORT=8890
EXPOSE 8890

CMD ["sh", "-c", "java -Xmx1g -cp resources:vendor/lib/*:vendor/peer-1.0.7622.jar:out Bridge"]
