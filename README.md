# Memorizer (Desktop + PWA)

Memorizer is a comprehensive spaced‑repetition learning system featuring a distraction‑minimized desktop application with a Stealth banner (Normal/Mini) for quick reviews, a main window for dashboards and planning, and a companion Progressive Web App (PWA) for mobile access with seamless data synchronization.

## Features

### Desktop Application
- **Stealth Banner** (Normal/Mini) with Dark/Light theme and taskbar-aware positioning
- **Advanced flip cycle**: Front → Back → All (Front+Back+Reading/Pos+Examples) → Front
- **Four rating buttons** (Again/Hard/Good/Easy) with clear tints in Dark and pastels in Light
- **Progress tracking** with Today x/target overlay (capped to target) and visual indicators
- **Deck management** with filter (All Decks or specific deck id) shared with PWA via DB
- **Manual data entry** (New Deck, New Entry) with Excel import/export capabilities
- **Tray controls** and serialized reminders (no overlapping banners)
- **Dashboard** with comprehensive statistics charts and task progress indicators
- **Multi-monitor support** with automatic positioning and taskbar overlay options
- **Preferences system** with runtime configuration persistence

### Web Interface
- **Browser-based access** when sync server is enabled (default port 7070/7071)
- **Full desktop functionality** including deck management, browsing, and study
- **Server-side rendering** with responsive design and dark/light theme support
- **Direct database access** without mobile pairing requirements
- **Cross-platform compatibility** accessible from any modern browser

### PWA (Progressive Web App)
- **Mobile-friendly interface** for on-the-go learning
- **Offline capabilities** with local data caching
- **Real-time synchronization** with desktop application via REST API
- **QR code pairing** for secure desktop-mobile connection
- **Responsive design** with dark/light theme support
- **Touch-optimized controls** for mobile learning experience

### SRS Algorithm
- **Spaced repetition engine** with configurable intervals
- **Advanced scheduling** with batch processing and snooze options
- **Performance tracking** with detailed review history
- **Adaptive difficulty** based on user ratings and response times

## Build & Run

### Prerequisites
- Java 8 JDK
- Maven 3.x
- Node.js 16+ (for PWA development)

### Desktop Application

1. Clean and package the project:
   ```bash
   mvn clean package
   ```
2. Run the application:
   ```bash
   java -jar target/memorizer-0.5.0-shaded.jar
   ```

### PWA Development

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

### Notes
- If Maven attempts to use a local Nexus, update `settings.xml` to point to Maven Central
- For environments without bundled JavaFX, use the `openjfx` profile:
  ```bash
  mvn -Popenjfx -Djavafx.platform=win clean package  # Use 'mac'/'linux' accordingly
  ```

## Configuration

### Desktop Application (`application.properties`)

Key settings are documented inline. Highlights:

- `app.db.path`: Path to the H2 database file (default: `./data/memo`)
- `app.h2.console.enabled`: Enable H2 web console (default: `true`)
- `app.h2.console.port`: Port for the H2 web console (default: `8084`)

**UI Settings** (now managed at runtime via Preferences dialog):
- `app.ui.mode`: Stealth mode (normal|mini)
- `app.ui.theme`: Theme for the Stealth drawer (dark|light)
- `app.window.*`: Window positioning and sizing options
- `app.ui.examples.*`: Examples display configuration

**Study Settings**:
- `app.study.daily-target`: Daily progress target (default: 50)
- `app.study.batch-size`: Number of cards in each batch
- `app.study.min/max-interval-minutes`: SRS interval boundaries
- `app.study.snooze-*`: Snooze behavior settings
- `app.deck.filter`: Deck filter (all or deck id)

**Web Server**:
- Default port: 7070 (HTTP) and 7071 (HTTPS)
- Supports both HTTP and HTTPS with self-signed certificates
- QR code generation for secure mobile pairing
- Server-side rendering for web interface at `/web` endpoint
- Static assets served from `/web/static/` (CSS, JS, images)

### Web Interface Configuration

The web interface provides full desktop functionality through the browser, accessing the same database as the desktop application. No additional configuration required beyond enabling the sync server.

### PWA Configuration

The PWA shares the same database as the desktop application and synchronizes data via the REST API. Configuration is handled automatically through the web interface.

## Usage

### Desktop Application

**Stealth Banner Shortcuts**:
- `SPACE/ENTER`: Flip card content
- `1/2/3/4`: Rate card (Again/Hard/Good/Easy)
- `ESC`: Hide banner (optional snooze)
- `M`: Toggle between Normal/Mini mode
- `T`: Toggle between Dark/Light theme
- `F8`: Show/Hide banner

**Main Window**:
- **Data menu**: New Deck, New Entry (adds note + card), Manage Decks, Sync Server
- **View menu**: Deck selection (All Decks or specific deck), Dashboard
- **Study menu**: Start study session, Exam mode
- **Help menu**: User Manual, About

**Web Interface Access**:
1. Enable sync server via Data → Sync Server
2. Open browser to `http://localhost:7070/web` (or `https://localhost:7071/web` if HTTPS is enabled)
3. Full desktop functionality available in browser

**PWA Pairing**:
1. Enable sync server via Data → Sync Server
2. Scan QR code with mobile device
3. Automatic synchronization begins

### Web Interface (Browser)

- **Home screen**: Quick access to Decks, Browse, and Study sections
- **Decks management**: Create, edit, and organize study decks
- **Browse mode**: Find and edit notes with full search capabilities
- **Study mode**: Browser-based card review with keyboard shortcuts
- **Theme toggle**: Switch between dark and light themes

### PWA (Mobile)

- **Decks screen**: Browse and select study decks
- **Study mode**: Touch-optimized card review interface
- **Connect screen**: Pair with desktop application
- **Sync controls**: Manual sync and full refresh options

## Dashboard Features

- Summary statistics: Due cards, New cards, Total cards, Total notes, Today's reviews, Plan progress
- Charts:
  - Daily Reviews (Last 7 Days) - Bar chart showing review activity
  - Rating Distribution - Bar chart showing rating frequency
  - Card Status Distribution - Bar chart showing card statuses
- Task Progress Indicator: Visual indicator showing today's task progress with color coding
  - Green: Completed tasks
  - Orange: Pending tasks
  - Gray: Not started tasks
  - For large plans (>60), switches to a compact progress bar with Done/Total label

## Architecture

### Database Schema
- **Decks**: Organizational units for cards
- **Notes**: Content storage with front/back text, reading, position, examples, and metadata
- **Cards**: Individual review cards with scheduling information (due dates, intervals, etc.)
- **Review Logs**: Detailed history of all review sessions

### SRS Algorithm
- Implements a spaced repetition system with configurable intervals
- Supports four rating levels with different interval multipliers
- Includes fuzz factor to prevent cards from becoming due at the same time
- Handles graduated cards and new cards separately

### Web API
- RESTful endpoints for deck, note, and card management
- Real-time synchronization between desktop and mobile
- Secure pairing using QR codes and temporary tokens
- Support for both HTTP and HTTPS connections

### PWA Technology Stack
- **Frontend**: Vue 3 with Composition API
- **Build Tool**: Vite
- **Database**: Dexie (IndexedDB wrapper)
- **Routing**: Vue Router 4
- **PWA**: Vite PWA plugin for offline capabilities

## Troubleshooting

- If the Stealth banner shows a gap near the taskbar, confirm overlay-taskbar=false and typical DPI (100–150%).
- If Today shows 54/50, this is capped visually to 50/50; the bar never exceeds 100%.
- If Examples don’t appear in Normal, ensure flip state is All (third click) and there is example content.
- If charts don't display data, ensure there is review history in the database.
- **PWA connection issues**: Check that the sync server is enabled and the QR code is scanned correctly.

### Database Issues

- **H2 Console**: Access via `http://localhost:8084` when enabled
- **Database location**: Default is `./data/memo` (configurable via `app.db.path`)
- **Backup**: Copy the database files (`memo.mv.db`, `memo.trace.db`) for backup

### Performance

- **Large decks**: Use deck filtering to limit the number of cards in study sessions
- **Memory usage**: Adjust batch size (`app.study.batch-size`) for optimal performance
- **Network sync**: Use Wi-Fi for faster synchronization between desktop and mobile

## Contributing

- Style: Java 8, SLF4J logging, 4‑space indentation
- Tests: JUnit 5 patterns can be added under src/test/java

## Goals & Roadmap

### Goals
- Deliver a fast, low-friction Stealth review banner with reliable positioning.
- Provide a simple Main window for overview and manual data entry (decks, notes).
- Keep Desktop and PWA aligned via shared DB, including an optional deck filter.
- Provide visual feedback through charts and progress indicators.

### Completed
- Stealth UI (Normal/Mini), Dark/Light themes, strict heights, progress overlay.
- Dark rating buttons with filled tints; Light pastel styles; proper alignment.
- Flip-cycle logic: Front → Back → Front+Back+Reading/Pos+Examples → Front.
- Examples in Normal (multiline scroller) and Mini (single-line; marquee).
- Dark popups (Edit/Add) with visible input fields and focus styling.
- Main/Study windows: top alignment, vertical scrolling, size to OS work area.
- Deck filter (View → Deck) with PlanService filtering; manual New Deck/New Entry.
- User Manual in Help menu; status-bar notices on create/save.
- Dashboard with statistics charts and task progress indicator.
- Application icons for all windows.
- Improved window sizing and layout.
- Sync server controls moved to Data menu.

### Pending / Next
- Stealth layout polish (baseline alignment, clamps) per checkpoint notes
- Optional: Exam hover/pressed states via CSS tokens
- Maven Central build reliability (avoid global Nexus mirrors) / CI
- Add tests (PlanService deck filter, Stealth flip states, repository paths)
- Persist per-deck challenge last-size hint
