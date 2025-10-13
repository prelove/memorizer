Task PWA-1: Desktop Server API Foundation
1. Objective
To embed a lightweight, secure web server into the existing JavaFX desktop application. This server will provide the necessary API foundation for the PWA to connect to.

2. Scope
Integrate the Javalin web server library into the Maven project.

Create a WebServerManager class to handle the server's lifecycle.

Implement a utility to programmatically generate a self-signed SSL certificate and keystore (.p12 or .jks) if one doesn't already exist.

Configure and start the Javalin server using the generated certificate to serve over HTTPS.

Create a basic /api/health endpoint to verify the server is running correctly.

Out of Scope: Any data synchronization APIs, serving PWA files, or QR code generation.

3. Technical Specifications
Web Server: Javalin

SSL Certificate Generation: Use Java's keytool command-line tool via a ProcessBuilder, or a library like Bouncy Castle for a pure Java implementation. The keystore should be saved in the application's data directory.

Server Configuration: The server should listen on 0.0.0.0 (all network interfaces) on a configurable port (default: 7070).

API Endpoint: GET /api/health should return a JSON object {"status": "ok", "version": "1.0"} with a 200 OK status.

4. Detailed Implementation Steps
Add Maven Dependency: Add the io.javalin:javalin dependency to the pom.xml.

Create CertificateManager.java:

Create a method generateSelfSignedCertificate() that checks if a keystore file (e.g., keystore.p12) exists in the app's data directory.

If it doesn't exist, use ProcessBuilder to execute a keytool command to generate it. Example command:

keytool -genkeypair -alias memorizer -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -validity 3650 -dname "CN=Desktop Memorizer Local" -storepass yourpassword -keypass yourpassword

This method should be called once when the server manager is initialized.

Create WebServerManager.java:

This class should have start() and stop() methods.

The start() method should:

Instantiate Javalin.create(config -> ...)

Inside the config, enable HTTPS by providing the path to the generated keystore and its password.

Define the GET /api/health route.

Call .start(7070).

The stop() method should call the javalin.stop() instance.

Integrate with Main App:

In the main dashboard UI, add a "Mobile Sync" section with a toggle switch or button to "Enable Sync Server".

This button will call WebServerManager.getInstance().start() and stop().

5. Definition of Done (DoD)
[ ] The Javalin dependency is added and the project builds successfully.

[ ] When "Enable Sync Server" is activated for the first time, a keystore.p12 file is created in the data directory.

[ ] The application successfully starts an HTTPS server on port 7070.

[ ] Accessing https://localhost:7070/api/health from a web browser (after bypassing the security warning) successfully returns the expected JSON response.

[ ] Disabling the sync server cleanly stops the web server.