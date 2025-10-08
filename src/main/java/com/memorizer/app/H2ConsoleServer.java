package com.memorizer.app;

import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/** Local H2 Web Console (http://127.0.0.1:port/). Not the DB itself. */
public final class H2ConsoleServer {
    private static final Logger log = LoggerFactory.getLogger(H2ConsoleServer.class);
    private static final Object LOCK = new Object();
    private static Server web; // org.h2.tools.Server

    /** Start console once if enabled in config. Safe to call repeatedly. */
    public static void startIfEnabled() {
        boolean enabled = Boolean.parseBoolean(Config.get("app.h2.console.enabled", "true"));
        if (!enabled) return;

        synchronized (LOCK) {
            if (web != null && web.isRunning(true)) {
                log.debug("H2 Console already running.");
                return;
            }
            String port = Config.get("app.h2.console.port", "8082");
            boolean allowOthers = Boolean.parseBoolean(Config.get("app.h2.console.allow-others", "false"));
            try {
                web = Server.createWebServer(
                        "-webPort", port,
                        allowOthers ? "-webAllowOthers" : "-webLocalhost"
                ).start();
                log.info("H2 Console started at http://127.0.0.1:{}/", port);
            } catch (SQLException e) {
                log.warn("Failed to start H2 Console: {}", e.toString());
            }
        }
    }

    /** Stop console. We only call this on application exit. */
    public static void stop() {
        synchronized (LOCK) {
            if (web != null) {
                try {
                    web.stop();
                    log.info("H2 Console stopped. (caller={})", caller());
                } finally {
                    web = null;
                }
            }
        }
    }

    private static String caller() {
        // Print a short call chain to locate the place that triggered stop()
        StackTraceElement[] s = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        for (int i = 3; i < Math.min(s.length, 8); i++) { // skip getStackTrace, caller(), stop()
            if (sb.length() > 0) sb.append(" <- ");
            sb.append(s[i].getClassName()).append(".").append(s[i].getMethodName())
              .append(":").append(s[i].getLineNumber());
        }
        return sb.toString();
    }

    private H2ConsoleServer() {}
}
