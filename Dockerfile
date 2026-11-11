# syntax=docker/dockerfile:1
# 스닙텍 API 서버 이미지 (설계: docs/deploy-design.md)
# - 빌드 단계는 빌드하는 기계의 아키텍처(BUILDPLATFORM)에서 그대로 돈다. 자바 바이트코드는 아키텍처와 무관해
#   arm64 이미지를 만들 때도 에뮬레이션 없이 빌드된다.
# - 실행 단계에는 RUN이 없어서 대상 아키텍처(arm64 등)용 에뮬레이터가 필요 없다.
# - 기본 이미지는 태그와 다이제스트를 함께 고정한다. 올릴 때는 두 값을 같이 바꾼다.

FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-noble@sha256:114cabb73033ca4ce7ceec3deb62396bc1b716282bae051ed3c1d82e38fcab2d AS build
WORKDIR /workspace

# 의존성 내려받기를 소스와 분리해 캐시한다 (빌드 설정이 그대로면 소스만 바뀌어도 다시 받지 않는다)
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon -q > /dev/null

# 테스트는 CI(./gradlew build)에서 이미 돌리므로 여기서는 실행 가능한 jar만 만든다
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -q \
 && cp "$(find build/libs -name '*.jar' ! -name '*-plain.jar')" app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-noble@sha256:0eda5830ed7b669abc246a8dc520ea8a23cab06f9a8d4b6964109e2f2394e46f
# 컨테이너 OS 시각(로그 등)도 한국 시간으로 맞춘다. JVM 기본 시간대는 앱이 시작할 때 Asia/Seoul로 고정한다
ENV TZ=Asia/Seoul
WORKDIR /app

# 기본 이미지에 있는 일반 사용자(ubuntu, uid 1000)로 실행한다. 사용자를 새로 만들면 RUN이 생겨 에뮬레이터가 필요해진다
# 자주 바뀌는 층을 뒤에 둔다 (의존성 → 로더 → 스냅숏 의존성 → 앱)
COPY --from=build --chown=1000:1000 /workspace/extracted/dependencies/ ./
COPY --from=build --chown=1000:1000 /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=1000:1000 /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=1000:1000 /workspace/extracted/application/ ./
USER 1000:1000

EXPOSE 8080
# 메모리 등 JVM 옵션은 실행할 때 JAVA_TOOL_OPTIONS로 넣는다 (#92)
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
