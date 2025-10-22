# Memorizer Project Overview

Memorizer is a desktop application designed for spaced repetition learning, featuring a distraction-minimized interface with a "Stealth" banner for quick reviews and a main window for dashboard, planning, and manual data entry. It also includes a companion Progressive Web App (PWA) that syncs data via a shared database.

## Key Technologies

- **Backend**: Java 8, JavaFX
- **Build Tool**: Maven
- **Database**: H2 (embedded)
- **Database Migration**: Flyway
- **Web Framework**: Javalin (v3.x for Java 8 compatibility)
- **PWA**: Vue 3, Vite

## Project Structure

- `src/main/java`: Java source code for the desktop application.
- `src/main/resources`: Application resources (CSS, properties, database migrations).
- `pwa/`: Source code for the Progressive Web App.
- `data/`: Default location for the H2 database files.
- `target/`: Maven build output directory.

## Building and Running

### Prerequisites

- Java 8 JDK
- Maven

### Desktop Application

1. Clean and package the project:
   ```bash
   mvn clean package
   ```
2. Run the application:
   ```bash
   java -jar target/memorizer-0.3.0-shaded.jar
   ```

### PWA

1. Navigate to the PWA directory:
   ```bash
   cd pwa
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Start the development server:
   ```bash
   npm run dev
   ```
4. Build for production:
   ```bash
   npm run build
   ```

## Configuration

### Desktop Application (`application.properties`)

- `app.db.path`: Path to the H2 database file (default: `./data/memo`).
- `app.h2.console.enabled`: Enable H2 web console (default: `true`).
- `app.h2.console.port`: Port for the H2 web console (default: `8082`).

Most UI and scheduler settings are managed at runtime via the application's Preferences dialog and are persisted to `data/prefs.properties`.

### Web Server

The desktop application includes an embedded web server (default port 7070) that serves the PWA and provides a REST API for synchronization. It supports both HTTP and HTTPS (with self-signed certificates or a local CA).

## Development Conventions

- **Language**: Java 8
- **Logging**: SLF4J with Logback
- **Code Style**: 4-space indentation
- **UI Framework**: JavaFX
- **Testing**: JUnit 5 (tests to be added under `src/test/java`)
- **PWA**: Vue 3 with Composition API, Vite for build tooling

## Features

- Stealth Banner (Normal/Mini) with Dark/Light theme
- Spaced repetition algorithm (SRS)
- Flip cycle for card content (Front → Back → All → Front)
- Rating system (Again/Hard/Good/Easy)
- Progress tracking
- Deck filtering (shared with PWA)
- Manual data entry (New Deck, New Entry)
- Excel import/export
- Tray controls and reminders
- PWA for mobile access with offline capabilities
- Secure pairing between desktop and mobile apps using QR codes