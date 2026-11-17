# 📚 Rincón de Lectura — Android Book Tracker

[![Android Build](https://img.shields.io/badge/Platform-Android-green.svg?style=flat-square)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-blue.svg?style=flat-square)](https://kotlinlang.org)
[![TDD](https://img.shields.io/badge/Methodology-Strict_TDD-red.svg?style=flat-square)](#-disciplina-strict-tdd)
[![Tests Passing](https://img.shields.io/badge/Tests-101%20/%20101%20Passed-brightgreen.svg?style=flat-square)](#-testing-strategy)

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

Actualmente la suite cuenta con **101 tests unitarios y de integración con 100% de éxito**.

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

---

## 🛠️ Calidad de Código y Hooks Locales

### Configuración inicial (una sola vez)
```bash
brew install lefthook gitleaks actionlint shellcheck
lefthook install
git config blame.ignoreRevsFile .git-blame-ignore-revs
```
`lefthook install` activa los hooks locales (pre-commit): `spotlessCheck`, Android Lint (`:app:lintDebug`, solo si tocas fuentes/recursos Android), `gitleaks` (secretos en el índice), `actionlint`, `shellcheck` y la validación de mensajes de commit (Conventional Commits, `scripts/check-commit-msg.sh`). El ajuste de `blame.ignoreRevsFile` oculta del `git blame` el commit de reformateo masivo.

### Comandos
| Objetivo | Comando |
|---|---|
| Verificar formato | `./gradlew spotlessCheck` |
| Aplicar formato | `./gradlew spotlessApply` |
| Android Lint (con baseline) | `./gradlew :app:lintDebug` |
| Regenerar el baseline de Lint | `./gradlew :app:updateLintBaseline` (revisa el diff de `app/lint-baseline.xml`) |
| Reporte de cobertura (Kover) | `./gradlew koverHtmlReportDebug` (sale en `app/build/reports/kover/htmlDebug/index.html`) |
| Pruebas unitarias | `./gradlew :app:testDebugUnitTest` |
| Pruebas del validador de commits | `scripts/test-check-commit-msg.sh` |
| Pruebas del resumen de cobertura | `scripts/test-kover-summary.sh` |

Lint falla ante cualquier warning nuevo (`warningsAsErrors`); los problemas existentes viven en el baseline. Kover es solo informativo: no hay umbral de cobertura.

---

## 🤖 Integración Continua (GitHub Actions)

`.github/workflows/ci.yml` corre en cada Pull Request y en cada push a `dev` (sin filtros de rutas). **El CI ejecuta solo las pruebas y los reportes; el análisis estático corre únicamente en local** mediante lefthook (pre-commit: `spotlessCheck`, `:app:lintDebug`, gitleaks, actionlint, shellcheck; commit-msg: Conventional Commits).

> **Riesgo aceptado**: como el análisis estático no se repite en CI, un commit hecho con `--no-verify`, desde la web de GitHub o sin `lefthook install` se salta lint, formato, secretos y la validación del mensaje de commit. Decisión explícita del proyecto: el análisis estático es solo local.

```text
unit-tests ──> ci-gate  (check requerido)
    └──> report (informativo, no bloquea)
```

- **`unit-tests`**: `:app:testDebugUnitTest` + Kover XML/HTML.
- **`ci-gate`**: único check requerido. Falla si `unit-tests` no termina en éxito (un job omitido cuenta como fallo). `report` no lo afecta.
- **`report`**: genera Allure 2.46.2 desde el JUnit XML y publica Allure + Kover en la rama `gh-pages` (`pr-<N>/` en PRs, `dev/` en pushes a `dev`), y deja un único comentario sticky en el PR con los enlaces y la cobertura de líneas y ramas. Solo PRs del mismo repositorio; los PRs desde forks solo suben el artefacto `report-site`.
- **`pr-cleanup.yml`**: borra `pr-<N>/` de `gh-pages` al cerrar el PR.
- **`promote.yml`**: exige que el último check-run `ci-gate` del SHA (emitido por GitHub Actions) sea `success` (es decir, que las pruebas pasaron); si falta o no es exitoso, la promoción falla.

Reportes publicados: `https://<usuario>.github.io/android-book-tracker/pr-<N>/allure/` y `.../kover/` (o `dev/` para la rama `dev`).

### Flujo de issues

GitHub solo interpreta `Closes #N` en PRs hacia la rama por defecto (`main`); en un PR hacia `dev` la palabra clave se ignora y no crea enlace. Por eso:

1. **Enlace**: crea la rama desde el issue con `gh issue develop <N> --base dev --name <tipo>/<N>-<slug> --checkout`. La rama y su PR aparecen en la sección *Development* del issue.
2. **Cierre**: la descripción del PR incluye `Closes #N`. Con squash merge, el mensaje del commit en `dev` es la descripción del PR, y el issue se cierra cuando ese commit llega a `main` mediante `promote.yml`.
3. **Resumen**: antes de pasar el issue a *Done*, se publica en él un comentario con lo hecho (PRs, cambios principales y cómo se verificó).

Estados del tablero: *In progress* (inicio) → *In review* (primer PR) → *In dev (QA)* (fusionado a `dev`) → *Done* (`qa:approved` y promovido a staging).

### Contrato de artefactos

| Artefacto | Contenido |
|---|---|
| `allure-results-<suite>` | JUnit XML de la suite (hoy `unit`). El job `report` fusiona todos los `allure-results-*`. |
| `coverage-<suite>` | Reportes Kover (`reportDebug.xml` + `htmlDebug/`). |
| `pit-<suite>` | Reservado para mutation testing (#6). |

Las suites futuras (contract, PIT, E2E) se adjuntan con estos nombres sin cambiar el job `report`.

### Pasos de administración (en orden, una sola vez)

1. Fusiona este cambio a `dev`; el primer push a `dev` crea la rama `gh-pages`.
2. **Settings → Pages**: fuente `Deploy from a branch`, rama `gh-pages`, carpeta `/ (root)`.
3. Tras la primera ejecución verde, agrega `ci-gate` (integración *GitHub Actions*) como *required status check* en el ruleset de `dev`.
4. **Settings → General → Pull Requests**: en squash merge, título = título del PR y mensaje = descripción del PR, para que `Closes #N` viaje en el commit de `dev` y cierre el issue al promover a `main`.

### Reversión

1. Quita `ci-gate` del ruleset de `dev`.
2. Revierte el PR de CI (restaura `promote.yml`).
3. Opcional: desactiva Pages, borra `gh-pages` y restaura los ajustes de squash merge.

---

*Creado con pasión e ingeniería de alta fidelidad. "Concepts > Code".*
