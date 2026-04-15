# Estágio de build com Maven e JDK 17
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copiar pom.xml e baixar dependências (cache)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copiar código fonte e compilar
COPY src ./src
RUN mvn clean package -DskipTests

# Estágio de runtime com JRE 17 Alpine
FROM amazoncorretto:17-alpine
WORKDIR /app

# Copiar o JAR gerado
COPY --from=builder /app/target/*-jar-with-dependencies.jar app.jar

# Porta que o servidor TCP escuta (interna)
EXPOSE 8080

# Comando de inicialização (certifique-se de que a classe principal está correta)
ENTRYPOINT ["java", "-jar", "app.jar"]