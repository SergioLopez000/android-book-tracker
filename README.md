# 📚 Rincón de Lectura — Android Book Tracker

[![Android Build](https://img.shields.io/badge/Platform-Android-green.svg?style=flat-square)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-blue.svg?style=flat-square)](https://kotlinlang.org)
[![TDD](https://img.shields.io/badge/Methodology-Strict_TDD-red.svg?style=flat-square)](#-disciplina-strict-tdd)
[![Tests Passing](https://img.shields.io/badge/Tests-102%20/%20102%20Passed-brightgreen.svg?style=flat-square)](#-testing-strategy)

**Rincón de Lectura** es una aplicación Android nativa, local-first y de un solo usuario, diseñada como biblioteca personal para registrar, calificar y reseñar libros leídos. 

El proyecto destaca por estar construido bajo la rigurosa metodología de **Desarrollo Guiado por Pruebas (Strict TDD)**, implementando una **Arquitectura Hexagonal limpia ("Ports and Adapters")**, totalmente desacoplada y con un diseño estético moderno inspirado en IMDb (Tema oscuro, carátulas dinámicas, selectores interactivos y calificaciones gráficas).

---

## ✨ Características Principales

*   **🎨 Interfaz Branded Dark**: Tema oscuro personalizado basado en Material Design 3 (`RinconDeLecturaTheme`), con colores y tipografía unificados y un fondo de ventana oscuro nativo desde el arranque.
*   **⭐ Calificación Gráfica Interactiva (`StarRating`)**: Visualización y entrada de calificaciones con precisión de media estrella (0.5 a 5.0) mediante un componente interactivo nativo en Compose.
*   **🖼️ Portadas Automáticas (`BookCoverImage`)**: Renderizado inteligente de portadas de libros a través de **Coil 3**, con placeholders/fallbacks locales seguros para URLs rotas o conexiones offline.
*   **📅 Filtros de Fecha y Calificación Combinados (AND)**: Filtra tu biblioteca con tres selectores mutuamente excluyentes (Último Mes, Último Año, Histórico) combinados de forma lógica (AND) con tu filtro de calificación mínima.
*   **🔍 Autocompletado de Metadatos Online**: Busca libros por título al agregarlos; la app consulta **Open Library API** y usa **Google Books API** como respaldo automático, prellenando el formulario de forma editable.
*   **💾 Base de Datos Local Offline (Room)**: Tus libros se guardan de forma 100% local en una base de datos Room SQLite altamente eficiente, con índices sobre las fechas para búsquedas instantáneas.

---

## 🏗️ Arquitectura del Software (Hexagonal Ports & Adapters)

Para garantizar un desacoplamiento absoluto de las herramientas de persistencia, red y vistas, el proyecto se divide de forma estricta en puertos y adaptadores dentro de un único módulo `:app`:

```text
com.librosleidos/
├── domain/            <-- Núcleo Puro Kotlin (Sin dependencias de Android ni librerías)
│   ├── model/         <-- Entidades de Negocio (Book, Rating, DateRangeFilter)
│   ├── port/          <-- Puertos de Salida (Interfaces: BookRepository, BookMetadataSource)
│   └── usecase/       <-- Reglas de Caso de Uso (SaveBook, GetBooks, DeleteBook)
├── data/              <-- Adaptadores de Infraestructura (Persistencia y Red)
│   ├── local/         <-- Room SQLite DB, BookDao, BookEntity, BookRepositoryImpl (Adapter)
│   └── remote/        <-- Clientes HTTP, OpenLibrary, GoogleBooks, ChainedMetadataSource
├── ui/                <-- Adaptador de Presentación (Jetpack Compose)
│   ├── components/    <-- Componentes Reusables Libres de Estado (StarRating, BookCoverImage)
│   ├── list/          <-- Lista de Libros (Route Container + Stateless Screen + ViewModel)
│   ├── edit/          <-- Edición/Adición (Route Container + Stateless Screen + ViewModel)
│   └── theme/         <-- Estilos Visuales, Colores (DarkColorScheme), Tipografía
└── di/                <-- Contenedor de Inyección de Dependencias Manual (AppContainer)
```

### Decisiones de Diseño Críticas

1.  **Inyección de Dependencias Manual (`AppContainer`)**: Sin frameworks pesados como Hilt para mantener los tests de Robolectric instantáneos y libres de runners complejos.
2.  **Encapsulamiento del Dominio (`Rating`)**: Las calificaciones se validan en el constructor de la clase inline `@JvmInline value class Rating`, asegurando que ningún libro se cree con un valor fuera del rango 0.5-5.0 o que no sea múltiplo de 0.5.
3.  **Búsqueda Encadenada Resiliente (`ChainedMetadataSource`)**: El caso de uso de búsqueda no sabe qué APIs existen; consulta el puerto y el adaptador ejecuta un patrón compuesto que avanza al segundo proveedor (Google Books) si el primero (Open Library) falla o no tiene coincidencias.

---

## 🔬 Disciplina Strict TDD & Testing Strategy

Cada caso de uso, consulta SQL, mapeo de datos, lógica de ViewModel y comportamiento de interfaz en Compose fue diseñado **test-first (RED-GREEN-REFACTOR)**. 

### Distribución de las Capas de Pruebas:

| Capa | Componentes Evaluados | Herramientas Utilizadas | Características |
|---|---|---|---|
| **Domain (Unit)** | Reglas de negocio, validaciones, calculador de rangos de fecha. | JUnit 4 | Pruebas de JVM instantáneas libres de Android. |
| **Data Local (Integration)** | Consultas SQL de ordenamiento, filtros AND y guardado. | Robolectric + Room InMemory DB | SQLite real compilado en memoria, testeable desde la JVM. |
| **ViewModel (Unit)** | Transiciones de estado de interfaz (`UiState`), re-consultas reactivas. | Kotlin Coroutines Test + Turbine | Evaluación de flujos asíncronos (`StateFlow`). |
| **Compose UI (Integration)** | Renderizado del tema oscuro, interacciones táctiles en estrellas, flujos de diálogo. | Robolectric + `createComposeRule` + `CoilTestRule` | Pruebas visuales y de interacción sobre la JVM sin emulador. |

Actualmente la suite cuenta con **102 tests unitarios y de integración con 100% de éxito**.

---

## 📁 Trazabilidad de Requerimientos (`openspec/`)

El proyecto implementa la metodología **Spec-Driven Development (SDD)**. En la carpeta `openspec/changes/` del repositorio, encontrarás toda la trazabilidad histórica de desarrollo de cada funcionalidad:

*   `openspec/changes/libros-leidos-android-book-tracker/`: El ciclo de vida completo del MVP (Requerimientos Gherkin, diagramas de flujo de datos, tareas TDD detalladas).
*   `openspec/changes/imdb-style-ui-and-filters/`: El ciclo de vida de la actualización estética, Coil 3, estrellas, filtros de fecha, renderizado de comentarios e iconografía.

---

## 🚀 Cómo Construir y Ejecutar

### Requisitos Previos
*   **JDK 17** o superior.
*   **Android SDK 36** o superior.

### 1. Clonar el repositorio
```bash
git clone https://github.com/SergioLopez000/android-book-tracker.git
cd android-book-tracker
```

### 2. Ejecutar la Suite de Pruebas (100% Verde en JVM)
```bash
./gradlew cleanTestDebugUnitTest :app:testDebugUnitTest
```

### 3. Generar el APK de Depuración
```bash
./gradlew assembleDebug
```
El APK ejecutable quedará generado exactamente en:
`app/build/outputs/apk/debug/app-debug.apk`

### 4. Instalar Directamente en tu Dispositivo por USB
Con el móvil conectado y la Depuración USB activa, ejecuta:
```bash
./gradlew installDebug
```

### 5. Pruebas end-to-end (Appium)
La suite `e2e/` maneja el APK de depuración en un emulador con Appium 3.9.1 + WebdriverIO 10.2.0 mediante Page Objects. Los datos son sintéticos y no necesita red. Solo el APK de depuración expone los tags de prueba como `resource-id`.
```bash
nvm install && nvm use          # lee .nvmrc (Node 24.22.3)
./gradlew :app:assembleDebug
cd e2e && npm ci
npm run e2e                     # con un emulador o dispositivo conectado
```
`E2E_APK_PATH=/ruta/app.apk npm run e2e` instala otro APK en lugar de `app/build/outputs/apk/debug/app-debug.apk`. Los resultados quedan en `e2e/allure-results` (JSON, con captura de pantalla solo en las pruebas fallidas). Más detalle en `e2e/README.md`.

---

## 🧩 Backend (Spring Boot)

API REST independiente en `backend/` (Kotlin, Spring Boot, PostgreSQL). Es un build de Gradle aparte del de la app: tiene su propio wrapper y se ejecuta desde `backend/`. El contrato vive en `backend/src/main/resources/openapi.yaml`.

**Requisitos**: JDK 21 y Docker en ejecución (las pruebas de integración y de contrato levantan PostgreSQL 17 con Testcontainers; no hay base de datos que instalar).

```bash
cd backend
./gradlew build            # compila, pruebas unitarias y de integración, verifica el jar empaquetado
./gradlew integrationTest  # PostgreSQL real vía Testcontainers
./gradlew contractTest     # REST Assured contra openapi.yaml (request y response)
./gradlew pitest           # mutation testing del dominio; falla por debajo de 80 %
./gradlew koverHtmlReport  # cobertura en build/reports/kover
```

Capas de pruebas: `test` (unitarias, sin base de datos), `integrationTest` (persistencia), `contractTest` (HTTP completo contra la especificación OpenAPI). El CI las corre en `backend-tests` y `contract-tests`.

**Postura de seguridad**: denegado por defecto y sin estado. Solo `/actuator/health` es público; todo lo demás (incluida `/api/books`) responde 401 hasta que el issue #26 añada autenticación real. Cada operación filtra por propietario en SQL.

### Imagen del backend (GHCR)

`backend/Dockerfile` es de una sola etapa sobre `gcr.io/distroless/java21-debian13:nonroot` fijada por digest, corre como `nonroot` y no lleva variables ni secretos; la configuración llega al arrancar (`SPRING_DATASOURCE_*`). El jar se compila en el runner.

| Evento | Resultado en `ghcr.io/<owner>/<repo>/backend` |
|---|---|
| Push a `dev` con `ci-gate` en verde (`publish-image`) | Etiquetas `sha-<commit>` y `dev` |
| Pull request o fork | No se publica nada |
| `promote.yml` | `check-image` exige que exista `sha-<commit>` **antes** de mover `main`; `retag-image` etiqueta ese mismo digest como `staging` con `crane` y comprueba que coincide |

El paquete queda privado. Prueba local: `scripts/test-image-smoke.sh` (construye el jar y la imagen, comprueba el usuario no root, arranca con un PostgreSQL desechable y espera 200 en `/actuator/health`). Las comprobaciones estáticas están en `scripts/test-image-config.sh`.

## 🛠️ Calidad de Código y Hooks Locales

### Configuración inicial (una sola vez)
```bash
brew install lefthook gitleaks actionlint shellcheck detekt
lefthook install
git config blame.ignoreRevsFile .git-blame-ignore-revs
```
`lefthook install` activa los hooks locales (pre-commit): `spotlessCheck`, Android Lint (`:app:lintDebug`, solo si tocas fuentes/recursos Android), `detekt` (solo sobre los `.kt` en el índice), `gitleaks` (secretos en el índice), `actionlint`, `secrets-contract` (lista de secretos permitidos, bloques `permissions:` e inventario `docs/secrets.md`, solo sobre workflows, `backend/Dockerfile`, el inventario y sus scripts), `shellcheck` y la validación de mensajes de commit (Conventional Commits, `scripts/check-commit-msg.sh`). El ajuste de `blame.ignoreRevsFile` oculta del `git blame` el commit de reformateo masivo.

Versiones: el CI fija gitleaks 8.30.1 y la configuración se probó con detekt 1.23.8. Las versiones de `brew` pueden diferir del CI; si un hook local y el CI discrepan, alinea la versión local.

**SonarQube for IDE** (recomendado, opcional): instala el plugin en tu IDE para ver los problemas de calidad mientras escribes, antes de llegar al hook.

### Comandos
| Objetivo | Comando |
|---|---|
| Verificar formato | `./gradlew spotlessCheck` |
| Aplicar formato | `./gradlew spotlessApply` |
| detekt sobre todo `app/src` | `detekt --input app/src --config config/detekt/detekt.yml --build-upon-default-config --baseline config/detekt/baseline.xml` |
| Regenerar el baseline de detekt | `detekt --input app/src --config config/detekt/detekt.yml --build-upon-default-config --create-baseline --baseline config/detekt/baseline.xml` (revisa el diff; el CI nunca lo regenera) |
| Android Lint (con baseline) | `./gradlew :app:lintDebug` |
| Regenerar el baseline de Lint | `./gradlew :app:updateLintBaseline` (revisa el diff de `app/lint-baseline.xml`) |
| Reporte de cobertura (Kover) | `./gradlew koverHtmlReportDebug` (sale en `app/build/reports/kover/htmlDebug/index.html`) |
| Pruebas unitarias | `./gradlew :app:testDebugUnitTest` |
| Pruebas del validador de commits | `scripts/test-check-commit-msg.sh` |
| Pruebas del resumen de cobertura | `scripts/test-kover-summary.sh` |
| Pruebas del rango del escaneo de secretos | `scripts/test-secret-scan-range.sh` |
| Contrato estático de secretos y permisos (solo local, hook de lefthook) | `scripts/test-secrets-contract.sh` (ejecuta `scripts/check-secrets-contract.sh`) |
| Analizador de la caducidad de `PROJECT_TOKEN` (solo local) | `scripts/test-token-expiry.sh` (ejecuta `scripts/token-expiry.sh`) |
| Aviso de caducidad de `PROJECT_TOKEN` con `gh` y `curl` simulados (solo local) | `scripts/test-token-expiry-notify.sh` (ejecuta `scripts/token-expiry-notify.sh` y `scripts/token-expiry-read.sh`) |

Lint falla ante cualquier warning nuevo (`warningsAsErrors`); los problemas existentes viven en el baseline. detekt usa `config/detekt/detekt.yml` (solo sobrescrituras sobre la configuración por defecto; el estilo y el formato los cubre Spotless) y falla ante cualquier problema nuevo; los existentes viven en `config/detekt/baseline.xml`. `--input` de detekt separa las rutas con comas. Kover es solo informativo: no hay umbral de cobertura.

---

## 🤖 Integración Continua (GitHub Actions)

`.github/workflows/ci.yml` corre en cada Pull Request y en cada push a `dev` (sin filtros de rutas). **El CI ejecuta las pruebas, los reportes y el escaneo de secretos; CodeQL corre en su propio workflow (`codeql.yml`). El análisis de calidad corre únicamente en local** mediante lefthook (pre-commit: `spotlessCheck`, `:app:lintDebug`, detekt, gitleaks, actionlint, shellcheck; commit-msg: Conventional Commits).

### Qué corre dónde

| Control | Local (lefthook) | CI |
|---|---|---|
| Formato (`spotlessCheck`) | Sí | No |
| Android Lint | Sí | No |
| detekt | Sí | No |
| actionlint / shellcheck | Sí | No |
| Mensaje de commit (Conventional Commits) | Sí | No |
| Pruebas unitarias + Kover | No | `unit-tests` |
| Backend: pruebas, integración, Kover | No | `backend-tests` |
| Backend: contrato (REST Assured) + PIT | No | `contract-tests` |
| E2E Appium en emulador (no bloquea) | No | `e2e-tests` |
| Verificación de TypeScript de `e2e/` (`tsc --noEmit`) | Sí (si hay `.ts` en el commit) | No |
| Imagen del backend a GHCR | No | `publish-image` (solo push a `dev`) |
| Secretos (gitleaks) | Solo el índice | `secret-scan` (rango de commits del evento, no se puede saltar con `--no-verify`) |
| CodeQL (Kotlin) | No | `codeql.yml`, bloquea por el ruleset |
| Actualizaciones de dependencias | No | Dependabot |

> **Riesgo aceptado**: los controles de calidad (formato, lint, detekt, shell, workflows y mensaje de commit) no se repiten en CI, así que un commit hecho con `--no-verify`, desde la web de GitHub o sin `lefthook install` se los salta. Decisión explícita del proyecto: la calidad es solo local. Los secretos sí se detectan en CI aunque el hook local se omita.

```text
unit-tests     ─┐
backend-tests  ─┼─> ci-gate  (check requerido) ──> publish-image (solo push a dev)
contract-tests ─┤
secret-scan    ─┘
e2e-tests      ──> (no bloquea: ci-gate no lo necesita)
unit-tests, backend-tests, contract-tests, e2e-tests ──> report (informativo, no bloquea)
codeql.yml: analyze ──> alertas de code scanning ──> regla del ruleset de dev
```

- **`unit-tests`**: `:app:testDebugUnitTest` + Kover XML/HTML.
- **`secret-scan`**: gitleaks 8.30.1 (fijado por sha256) sobre el rango de commits del evento, calculado por `scripts/secret-scan-range.sh`. PR: `BASE..HEAD`; push a `dev`: `BEFORE..AFTER`; rama nueva (`BEFORE` todo ceros): solo el commit final. Un SHA mal formado o un `BEFORE` que no existe en el clon hace fallar el job (nunca escanea menos de lo debido). La salida va con `--redact`.
- **`backend-tests`** y **`contract-tests`**: JDK 21; el segundo corre `contractTest` y el gate de mutación `pitest`. Suben `allure-results-*`, `coverage-*` y `pit-*`.
- **`e2e-tests`**: no bloquea (`ci-gate` no lo necesita y su fallo no lo hace fallar). Corre en cada PR y push a `dev`, sin secretos y con `permissions: contents: read`: compila el APK de depuración, comprueba con `scripts/test-release-no-test-tags.sh` que el release no expone tags de prueba, arranca un emulador (API 34, target `default`, x86_64, con KVM; el AVD se cachea y solo se guarda en pushes a `dev`) y ejecuta `npm run e2e` en modo avión. Sube `allure-results-e2e` (JSON, 7 días) siempre, incluso si falla. `scripts/test-e2e-ci-config.sh` revisa esta configuración de forma estática.
- **`publish-image`**: compila el jar y publica la imagen en GHCR (ver arriba). Necesita `ci-gate`, pero `ci-gate` no la necesita, así que nunca bloquea un merge. Es el único job de `ci.yml` con `packages: write`.
- **`ci-gate`**: único check requerido. Falla si `unit-tests`, `backend-tests`, `contract-tests` o `secret-scan` no terminan en éxito (un job omitido cuenta como fallo). `report` y CodeQL no lo afectan.
- **`codeql.yml`**: análisis CodeQL de Kotlin (`build-mode: manual`) en PRs hacia `dev`, pushes a `dev` y un cron semanal. Bloquea mediante la regla "Require code scanning results" del ruleset (alertas High o superiores), no mediante `ci-gate`.
- **Dependabot** (`.github/dependabot.yml`): actualizaciones de versión semanales de Gradle (raíz y `/backend`), de la imagen base Docker de `/backend` (bump del digest) y de npm de `/e2e` (grupos `wdio`, `appium` y `typescript`; las versiones mayores de Appium, el driver, WebdriverIO y `@wdio/*` se ignoran y se suben a mano) y de GitHub Actions hacia `dev` (`build(deps):` / `ci(deps):`), agrupadas. En PRs de Dependabot `report` no publica ni comenta (su token es de solo lectura).
- **`report`**: espera a `unit-tests`, `backend-tests`, `contract-tests` y `e2e-tests`, genera Allure 2.46.2 desde el JUnit XML y el JSON de e2e (con la captura de las pruebas fallidas) y publica Allure + Kover en la rama `gh-pages` (`pr-<N>/` en PRs, `dev/` en pushes a `dev`), y deja un único comentario sticky en el PR con los enlaces y la cobertura de líneas y ramas. Solo PRs del mismo repositorio; los PRs desde forks solo suben el artefacto `report-site`.
- **`pr-cleanup.yml`**: borra `pr-<N>/` de `gh-pages` al cerrar el PR.
- **`promote.yml`**: exige que el último check-run `ci-gate` del SHA (emitido por GitHub Actions) sea `success` (es decir, que las pruebas y el escaneo de secretos pasaron); si falta o no es exitoso, la promoción falla. Antes de mover `main`, `check-image` exige que la imagen `sha-<commit>` exista en GHCR; después de moverla, `retag-image` la etiqueta como `staging` (mismo digest, sin reconstruir). Es el único otro job con `packages: write`.

Reportes publicados: `https://<usuario>.github.io/android-book-tracker/pr-<N>/allure/` y `.../kover/` (o `dev/` para la rama `dev`).

### Flujo de issues

GitHub solo interpreta `Closes #N` en PRs hacia la rama por defecto (`main`); en un PR hacia `dev` la palabra clave se ignora y no crea enlace. Por eso:

1. **Enlace**: crea la rama desde el issue con `gh issue develop <N> --base dev --name <tipo>/<N>-<slug> --checkout`. La rama aparece en la sección *Development* del issue y el PR queda en su historial gracias a la mención `#N` de la descripción. La API de GitHub no permite enlazar un PR hacia `dev` en *Development*; solo se puede hacer a mano desde la web.
2. **Cierre**: los issues se cierran a mano. `Closes #N` en el PR no los cierra: GitHub lo ignora en PRs hacia `dev`, y la promoción a `main` con `promote.yml` tampoco los cierra (comprobado en #39). Orden: `qa:approved` → comentario de resumen → promoción a staging → cerrar el issue → *Done*.
3. **Resumen**: antes de pasar el issue a *Done*, se publica en él un comentario con lo hecho (PRs, cambios principales y cómo se verificó).

#### Formularios

`.github/ISSUE_TEMPLATE/` ofrece dos formularios y desactiva los issues en blanco (`blank_issues_enabled: false`):

- **Feature** (`type:feature`): historia de usuario, criterios de aceptación Given/When/Then, checklist de QA en `dev` (con notas opcionales), Definition of Done (cambio SDD enlazado, tests primero y en verde, hooks + CodeQL + secret-scan en verde, QA manual registrada, `qa:approved` y promoción) y área.
- **Bug** (`type:bug`): pasos para reproducir, comportamiento esperado y actual, entorno, severidad y logs.

Los formularios no llevan la clave `projects:`: el tablero (Project #2) añade los issues con su auto-add (`is:issue`). El tablero contiene solo issues, nunca PRs.

#### Transiciones del tablero

| Estado | Cómo se pone |
|---|---|
| *Backlog*, *Ready*, *In progress* | A mano |
| *In review* | Automático: `board-sync.yml` al abrir, reabrir o marcar como listo (*ready for review*) un PR no borrador hacia `dev` |
| *In dev (QA)* | Automático: `board-sync.yml` al fusionar el PR a `dev` |
| *Done* | Al cerrar el issue a mano, el workflow integrado "Item closed" lo mueve |

`board-sync.yml` resuelve el issue con `scripts/resolve-issue-number.sh`: gana el número de la rama `<tipo>/<N>-<slug>` y, si la rama no lo tiene, se usa la primera palabra clave `Closes`, `Fixes` o `Resolves` seguida de `#N` en la descripción del PR (`Refs #N` se ignora). Si el issue no está en el tablero, lo añade antes de moverlo. Nunca mueve una tarjeta hacia atrás, nunca cierra el issue ni comenta, y no es un check requerido (`ci-gate` no depende de él). Se omite en PRs de forks y de Dependabot, y si el secreto `PROJECT_TOKEN` no existe solo emite un aviso y termina en verde.

#### Token `PROJECT_TOKEN`

`GITHUB_TOKEN` no puede escribir en Projects v2 de una cuenta personal, así que el workflow usa un PAT clásico.

1. **Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token (classic)**.
2. Marca **solo** el scope `project` y define una expiración finita (90 días como máximo).
3. En el repositorio: **Settings → Secrets and variables → Actions → New repository secret**, nombre `PROJECT_TOKEN`.

Rotación: antes de que expire, genera un PAT nuevo con las mismas condiciones, reemplaza el valor del secreto y revoca el anterior. Un token caducado o inválido hace fallar el job con un error de GraphQL (el check no es requerido, pero el fallo queda visible); un secreto ausente solo muestra un aviso.

#### Pasos de administración del tablero (en orden, una sola vez)

1. Crea `PROJECT_TOKEN` (sección anterior), antes de abrir el PR que añade el workflow.
2. En **Project #2 → Workflows** (solo desde la interfaz web):
   - **Auto-add to project**: cambia el filtro a `is:issue`.
   - **Pull request merged**: desactívalo.
   - **Item closed**: déjalo activado.
   - **Auto-close issue**: déjalo desactivado.
3. Archiva los PRs que ya estaban en el tablero (`gh project item-archive 2 --owner SergioLopez000 --id <id>`; es reversible con `--undo`).

### Secretos y rotación

Los secretos viven solo en sus almacenes: secretos del repositorio para la automatización de CI (hoy únicamente `PROJECT_TOKEN`), almacenes por entorno (`dev`, `staging`) del proveedor de alojamiento para el backend y `local.properties` para el desarrollo local. Nunca en el repositorio, en el `Dockerfile` ni en los logs.

- Inventario, propietario, ubicación y procedimiento de rotación: [`docs/secrets.md`](docs/secrets.md) (punto de entrada para rotar cualquier secreto, sección `Runbook`).
- Aviso de caducidad de `PROJECT_TOKEN`: el workflow [`token-expiry.yml`](.github/workflows/token-expiry.yml) se ejecuta cada lunes (y a mano con `workflow_dispatch`), lee la fecha de caducidad del token y abre o actualiza un único issue con la etiqueta `secret-rotation` cuando quedan menos de 14 días, el token ya caducó o GitHub lo rechaza. Si no puede determinar la fecha, abre un issue de aviso distinto. Tras fusionarlo a la rama por defecto, lánzalo una vez a mano para confirmar el formato de la cabecera. Al rotar el token, actualiza la fecha en el inventario y cierra el issue a mano.
- Cadencia: PAT clásico como máximo 90 días; contraseñas de base de datos 180 días o ante una fuga; claves de API de proveedores 365 días o ante una fuga; secretos de firma 180 días; el keystore de release no se rota; `GITHUB_TOKEN` es automático.
- `bash scripts/test-secrets-contract.sh` falla si un `secrets.*` de los workflows no está en el inventario (solo local, hook de lefthook).

### Contrato de artefactos

| Artefacto | Contenido |
|---|---|
| `allure-results-<suite>` | JUnit XML de la suite (`unit`, `backend`, `contract`); `allure-results-e2e` contiene JSON de Allure y se conserva 7 días. El job `report` fusiona todos los `allure-results-*`. |
| `coverage-<suite>` | Reportes Kover (`reportDebug.xml` + `htmlDebug/`). |
| `pit-<suite>` | Reservado para mutation testing (#6). |

Las suites futuras se adjuntan con estos nombres sin cambiar el job `report`.

### Pasos de administración (en orden, una sola vez)

1. Fusiona este cambio a `dev`; el primer push a `dev` crea la rama `gh-pages`.
2. **Settings → Pages**: fuente `Deploy from a branch`, rama `gh-pages`, carpeta `/ (root)`.
3. Tras la primera ejecución verde, agrega `ci-gate` (integración *GitHub Actions*) como *required status check* en el ruleset de `dev`.
4. Tras el primer análisis verde de CodeQL en un push a `dev`, agrega al ruleset de `dev` la regla **Require code scanning results** (herramienta CodeQL, alertas = Errors, severidad de seguridad = High o superior). Si la agregas antes, bloquea todos los PRs.
5. Activa las alertas de Dependabot y después las actualizaciones de seguridad (**Settings → Advanced Security**, o `PUT /repos/{o}/{r}/vulnerability-alerts` y luego `PUT /repos/{o}/{r}/automated-security-fixes`). Al promover a `main`, `dependabot.yml` y el cron de CodeQL se activan (se leen desde la rama por defecto).
6. **Settings → General → Pull Requests**: en squash merge, título = título del PR y mensaje = descripción del PR, para que el commit de `dev` conserve la descripción del PR con sus referencias `#N`.

### PRs de seguridad de Dependabot

Las actualizaciones de seguridad apuntan a `main`. Para que CodeQL y `ci-gate` corran contra `dev`: cambia la base del PR a `dev` y **ciérralo y reábrelo** (cambiar la base solo dispara `edited`, que no inicia los workflows). Si `@dependabot rebase` demuestra rebasar sobre `dev`, también sirve.

### Reversión

1. Quita la regla de code scanning del ruleset de `dev` (sin `codeql.yml` bloquearía todos los PRs).
2. Quita `ci-gate` del ruleset de `dev` si también revertirás el CI original.
3. Revierte el PR de seguridad y calidad (`ci-gate` vuelve a depender solo de `unit-tests`; se retiran CodeQL, Dependabot y detekt) y, si hace falta, el de CI (restaura `promote.yml`).
4. Opcional: desactiva las actualizaciones de seguridad y las alertas, desactiva Pages, borra `gh-pages` y restaura los ajustes de squash merge.
5. Sincronización del tablero, en este orden: revierte `board-sync.yml` (los formularios y los scripts son independientes); borra el secreto `PROJECT_TOKEN` y revoca el PAT; si quieres, restaura los PRs archivados con `gh project item-archive 2 --owner SergioLopez000 --id <id> --undo`; restaura el filtro de auto-add y reactiva "Pull request merged". Las tarjetas ya movidas se quedan como están.

---

*Creado con pasión e ingeniería de alta fidelidad. "Concepts > Code".*
