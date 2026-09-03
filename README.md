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

*Creado con pasión e ingeniería de alta fidelidad. "Concepts > Code".*
