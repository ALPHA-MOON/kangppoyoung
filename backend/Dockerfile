# --- build stage ---
# 이 프로젝트는 Java 25(toolchain) + Gradle 9.5.1(wrapper) 기준이다.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
# gradlew 가 호스트(Windows)에서 CRLF 로 체크아웃되면 리눅스에서 셔뱅이 깨지므로 CR 제거.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew clean bootJar --no-daemon -x test

# --- runtime stage ---
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
# 시크릿은 ENV/ARG 로 굽지 않는다. 런타임에 env_file 로 주입한다.
ENTRYPOINT ["java", "-jar", "app.jar"]
