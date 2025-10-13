Task PWA-3: Connection & Pairing Flow
1. Objective
To implement the "Guided Local Secure Connection" flow, enabling the PWA to securely connect to the desktop application server using a QR code.

2. Scope
Desktop Application:

Integrate a QR code generation library.

Create a "Mobile Sync" UI panel in the main dashboard.

This panel will display the server's local HTTPS URL as a scannable QR code.

Crucially, the panel must also display clear, step-by-step graphical instructions (as described in pwa-sync-solution.md) guiding the user on how to bypass the browser's security warning for the self-signed certificate.

PWA:

Add a QR code scanning library.

Create a "Settings" or "Connect" view.

This view will have a button that opens the device's camera to scan the QR code.

Upon a successful scan, the PWA will save the server's URL to its localStorage.

Out of Scope: Actual data synchronization. This task only covers establishing the connection link.

3. Technical Specifications
Desktop QR Generation: ZXing (com.google.zxing).

PWA QR Scanning: html5-qrcode or a similar library.

Desktop UI: The guide must be very clear, with images/icons for different mobile OS (iOS/Android) showing exactly where to tap to accept the security risk.

PWA Storage: The server URL should be stored in localStorage under a key like server_url.

4. Detailed Implementation Steps
Desktop: Add QR Library: Add the ZXing core and javase dependencies to pom.xml.

Desktop: Create SyncPanel.java:

This JavaFX component will be added to the main dashboard.

It should contain an ImageView for the QR code and a VBox for the instructional text and images.

When the sync server is enabled:

Detect the machine's local IP address (e.g., 192.168.x.x).

Construct the URL: https://<ip>:7070.

Use the ZXing library to generate a QR code image from this URL string.

Display the image in the ImageView.

Display the instructional guide.

PWA: Add QR Scanner Library: npm install html5-qrcode.

PWA: Create views/ConnectView.vue:

This view should contain a placeholder for the camera feed and a "Scan to Connect" button.

When the button is clicked, initialize Html5Qrcode and start scanning.

Define a success callback onScanSuccess(decodedText, decodedResult).

Inside the callback:

Validate that decodedText is a valid HTTPS URL.

Save the URL to localStorage.setItem('server_url', decodedText).

Provide feedback to the user ("Connected successfully!") and navigate away from the connect view.

5. Definition of Done (DoD)
[ ] The desktop app correctly detects its local IP and displays a scannable QR code for the HTTPS URL.

[ ] The desktop app displays a clear, user-friendly guide for bypassing the security warning.

[ ] The PWA can open a camera view to scan for QR codes.

[ ] Scanning the QR code on the desktop successfully saves the server URL into the PWA's localStorage.

[ ] The PWA provides clear visual feedback upon successful connection.