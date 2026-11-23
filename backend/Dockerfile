# Runtime image only: the jar is built on the runner (`./gradlew bootJar`) and copied in.
# The base is pinned by digest; Dependabot (docker ecosystem) proposes digest bumps.
# Platform: linux/amd64 (the digest below is the multi-arch index; builds select amd64).
FROM gcr.io/distroless/java21-debian13:nonroot@sha256:128bd878463537eef7771b50fecf68faace9089f654a2d233f1c4f344e6817d9
LABEL org.opencontainers.image.source="https://github.com/SergioLopez000/android-book-tracker"
COPY build/libs/backend.jar /app/backend.jar
USER nonroot
EXPOSE 8080
ENTRYPOINT ["/usr/bin/java", "-jar", "/app/backend.jar"]
