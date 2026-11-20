#!/usr/bin/env bash
# Local smoke test for the backend image: builds the jar and the image, checks the
# runtime user is non-root, then starts the image next to a throwaway PostgreSQL
# (dummy credentials passed at run time, never baked in) and expects
# /actuator/health to return 200 and anonymous /api/books to return 401.
# Requires a reachable Docker daemon (set DOCKER_HOST for Rancher Desktop).
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
suffix="$$"
image="libros-leidos-backend:smoke-$suffix"
net="smoke-net-$suffix"
db="smoke-db-$suffix"
app="smoke-app-$suffix"

cleanup() {
  docker rm -f "$app" "$db" >/dev/null 2>&1 || true
  docker network rm "$net" >/dev/null 2>&1 || true
  docker rmi -f "$image" >/dev/null 2>&1 || true
}
trap cleanup EXIT

(cd "$root/backend" && ./gradlew --no-daemon -q bootJar)
docker build -q -f "$root/backend/Dockerfile" -t "$image" "$root/backend" >/dev/null

user="$(docker inspect --format '{{.Config.User}}' "$image")"
case "$user" in
  "" | 0 | root | 0:*) echo "FAIL: image runs as '$user'"; exit 1 ;;
esac
echo "ok: image user is '$user'"

docker network create "$net" >/dev/null
docker run -d --name "$db" --network "$net" \
  -e POSTGRES_DB=smoke -e POSTGRES_USER=smoke -e POSTGRES_PASSWORD=smoke-dummy \
  postgres:17-alpine >/dev/null
for _ in $(seq 1 30); do
  docker exec "$db" pg_isready -U smoke -d smoke >/dev/null 2>&1 && break
  sleep 1
done

docker run -d --name "$app" --network "$net" -p 127.0.0.1::8080 \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://$db:5432/smoke" \
  -e SPRING_DATASOURCE_USERNAME=smoke \
  -e SPRING_DATASOURCE_PASSWORD=smoke-dummy \
  "$image" >/dev/null
port="$(docker port "$app" 8080/tcp | head -n1 | sed 's/.*://')"

status=""
for _ in $(seq 1 60); do
  status="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/actuator/health" || true)"
  [[ "$status" == "200" ]] && break
  sleep 1
done
if [[ "$status" != "200" ]]; then
  echo "FAIL: /actuator/health returned '$status'"
  docker logs "$app" | tail -n 30
  exit 1
fi
echo "ok: /actuator/health 200"

books="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/api/books")"
if [[ "$books" != "401" ]]; then
  echo "FAIL: anonymous /api/books returned '$books', expected 401"
  exit 1
fi
echo "ok: anonymous /api/books 401"
echo "image smoke test passed"
