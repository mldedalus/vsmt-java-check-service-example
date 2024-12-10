# Maven clean/install does not need to be run on ARM (.jar output is not compiled to native binary)
FROM --platform=${BUILDPLATFORM:-linux/amd64} eclipse-temurin:17-jdk-alpine as builder
# FROM eclipse-temurin:17-jdk-jammy as builder

WORKDIR /app

COPY . .

RUN ./mvnw clean install -DskipTests

FROM eclipse-temurin:17-jre-jammy as packager

RUN addgroup --system vsmt && adduser --system vsmt --ingroup vsmt
USER vsmt

COPY --from=builder /app/target/*.jar /opt/*.jar

ENTRYPOINT ["java","-jar","/opt/*.jar"]