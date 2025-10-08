package com.memorizer.app;

import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * Embedded H2 Web Console (localhost only by default).
 * Start/stop controlled from system tray menu.
 */
public final class H2ConsoleServer {
    private static final Logger log = LoggerFactory.getLogger(H2ConsoleServer.class);
    private static Server server;

    public static synchronized void startIfEnabled() {
        boolean enabled = Config.getBool("app.h2.console.enabled", true);
        if (!enabled || server != null) return;

        int port = Config.getInt("app.h2.console.port", 8082);
        boolean allowOthers = Config.getBool("app.h2.console.allow-others", false);

        try {
            server = Server.createWebServer(
                    "-webPort", String.valueOf(port),
                    allowOthers ? "-webAllowOthers" : "-webDaemon"
            ).start();
            log.info("H2 Console started at http://127.0.0.1:{}/", port);
        } catch (SQLException e) {
            log.warn("Failed to start H2 Console: {}", e.getMessage());
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop();
            log.info("H2 Console stopped.");
            server = null;
        }
    }

    private H2ConsoleServer() {}
}
