AI Agent Master Plan: PWA Companion App
1. Project Goal
The objective of this series of tasks is to develop a Progressive Web App (PWA) that acts as a mobile companion to the "Desktop Memorizer" application. This PWA will allow users to continue their learning and review sessions on their mobile devices, with full offline capabilities, and synchronize their progress with the main desktop application over their local Wi-Fi network.

2. Core Architecture
Desktop Application (Server Role): The existing JavaFX application will embed a lightweight web server (Javalin). This server will run on the user's local machine and will be responsible for:

Serving the PWA's static files (HTML, CSS, JS).

Providing a RESTful API for data synchronization.

Implementing the "Guided Local Secure Connection" protocol, which involves generating a self-signed SSL certificate and providing a user-friendly guide for mobile device pairing.

Mobile App (PWA Client): A modern, lightweight web application designed for mobile browsers.

Frontend Tech: Vite + Vue 3 (or Svelte, as chosen by the AI).

Offline Storage: IndexedDB will be the local database on the mobile device, storing all decks, notes, cards, and review logs.

Offline Capability: A Service Worker will cache the application shell and data, making the PWA fully functional without a network connection after the initial setup.

3. Task Execution Sequence
The following tasks must be executed in strict sequential order. Each file details a self-contained step.

tasks/task-pwa-1.md: Implement the foundational web server within the desktop JavaFX application, including setting up HTTPS with a self-signed certificate.

tasks/task-pwa-2.md: Set up the frontend PWA project with its basic UI components and local database structure, using mock data.

tasks/task-pwa-3.md: Implement the user-facing connection and pairing flow, featuring QR code generation on the desktop and scanning on the PWA.

tasks/task-pwa-4.md: Develop the core two-way data synchronization logic between the PWA and the desktop server.

tasks/task-pwa-5.md: Solidify the PWA's offline capabilities by fully implementing the Service Worker and ensuring all operations work without a network connection.