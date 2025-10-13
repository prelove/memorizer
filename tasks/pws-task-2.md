Task PWA-2: PWA Project Setup & Basic UI
1. Objective
To create the frontend PWA project, including the core UI components for learning and a local IndexedDB database schema, all populated with mock data.

2. Scope
Set up a new frontend project using Vite and Vue 3.

Install and configure vite-plugin-pwa to enable PWA capabilities (manifest generation, service worker).

Create the primary UI components: DeckListView.vue, CardView.vue.

Design and implement a simple local database service using IndexedDB to store card data.

The UI should be functional but will operate entirely on hardcoded/mock data.

Out of Scope: Any communication with the desktop server, QR code scanning, or real data synchronization.

3. Technical Specifications
Framework: Vite + Vue 3

PWA Plugin: vite-plugin-pwa

Local Database: IndexedDB (can use a wrapper library like dexie.js to simplify).

UI Design: Clean, mobile-first, touch-friendly.

IndexedDB Schema:

A database named memorizerDB.

Object Stores:

decks: key id, indexes name.

notes: key id, indexes deckId.

cards: key id, indexes noteId, dueAt.

review_logs: key id (auto-incrementing), indexes cardId, synced.

4. Detailed Implementation Steps
Initialize Project: Run npm create vite@latest my-pwa -- --template vue.

Install Dependencies: npm install dexie vite-plugin-pwa.

Configure vite.config.js: Import VitePWA and add it to the plugins array. Configure the manifest.json options with the app name, icons, etc.

Create database.js:

Import Dexie.

Define the database schema as specified above.

Export the initialized db object.

Create a function populateMockData() that clears and fills the tables with a few sample decks and cards if the database is empty.

Create UI Components:

App.vue: Main router view.

views/DeckListView.vue: Fetches and displays a list of decks from the mock data in IndexedDB. Clicking a deck navigates to the CardView.

views/CardView.vue: The main learning interface.

It fetches the next due card from IndexedDB for a given deck.

Displays the card's front field.

Has a "Flip" button to reveal the back.

Has four rating buttons ("Again", "Hard", "Good", "Easy"). Clicking a button should log the action and load the next card.

Initial Data Load: In main.js or App.vue, call the populateMockData() function to ensure the app has data on first launch.

5. Definition of Done (DoD)
[ ] The Vite project is set up and runs via npm run dev.

[ ] The vite-plugin-pwa generates a valid manifest.webmanifest and sw.js.

[ ] When the app starts, the IndexedDB is created with the correct schema and populated with mock data.

[ ] The DeckListView correctly displays the list of mock decks.

[ ] Clicking a deck opens the CardView, which displays the first card.

[ ] The flip and rating buttons in CardView are functional (can log to console and load the next mock card).

[ ] The web app can be "Added to Home Screen" on a mobile device.