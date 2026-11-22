# ThisIsTea images. Two targets, both built from the repo root by the compose files:
#   server  the Ktor API (JRE + installDist + content)
#   web     Caddy serving the wasm build at / and proxying /api/* to the server (deploy/Caddyfile)
# The JDK image + the Gradle wrapper keep the Gradle version identical to local builds.
FROM eclipse-temurin:21-jdk AS source
WORKDIR /src
COPY . .
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

FROM source AS server-build
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew -Ptea.android=false :server:installDist --no-daemon --console=plain

FROM source AS web-build
# The Node.js that the Kotlin/Wasm plugin downloads needs libatomic.
RUN apt-get update && apt-get install -y --no-install-recommends libatomic1 && rm -rf /var/lib/apt/lists/*
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew -Ptea.android=false :composeApp:wasmJsBrowserDistribution --no-daemon --console=plain

FROM caddy:2-alpine AS web
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=web-build /src/composeApp/build/dist/wasmJs/productionExecutable /srv

FROM eclipse-temurin:21-jre AS server
WORKDIR /app
COPY --from=server-build /src/server/build/install/server /app
# Items (for leaderboard XP) and field templates are read at runtime.
COPY content /app/content
ENV CONTENT_DIR=/app/content \
    PORT=8080
EXPOSE 8080
CMD ["/app/bin/server"]
