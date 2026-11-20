# Runtime image only: the jar is built on the runner (`./gradlew bootJar`) and copied in.
# The base is pinned by digest; Dependabot (docker ecosystem) proposes digest bumps.
# Platform: linux/amd64 (the digest below is the multi-arch index; builds select amd64).
FROM gcr.io/distroless/java21-debian13:nonroot@sha256:cddca4cc6b1dfc76142e5c1a1d00ce7a79bcc1292ad65d34227794f9d371a496
LABEL org.opencontainers.image.source="https://github.com/SergioLopez000/android-book-tracker"
COPY build/libs/backend.jar /app/backend.jar
USER nonroot
EXPOSE 8080
ENTRYPOINT ["/usr/bin/java", "-jar", "/app/backend.jar"]
