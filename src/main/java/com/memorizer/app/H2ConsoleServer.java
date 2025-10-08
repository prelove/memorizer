package com.memorizer.app;

import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Local H2 Web Console (http://127.0.0.1:PORT/). Not the DB itself. */
public final class H2ConsoleServer {
    private static final Logger log = LoggerFactory.getLogger(H2ConsoleServer.class);
    private static final Object LOCK = new Object();
    private static Server web; // org.h2.tools.Server

    /** Start console once if enabled in config. Safe to call repeatedly. */
    public static void startIfEnabled() {
        if (!Boolean.parseBoolean(Config.get("app.h2.console.enabled", "true"))) return;

        synchronized (LOCK) {
            if (web != null && web.isRunning(true)) {
                log.debug("H2 Console already running.");
                return;
            }
            final String port = Config.get("app.h2.console.port", "8082");
            final boolean allowOthers = Boolean.parseBoolean(Config.get("app.h2.console.allow-others", "false"));

            // Build args for H2 1.4.200 compatibility
            List<String> args = new ArrayList<String>();
            args.add("-webPort"); args.add(port);
            if (allowOthers) args.add("-webAllowOthers"); // DON'T add -webLocalhost (unsupported in 1.4.200)

            try {
                log.info("Starting H2 Console with args={}", args);
                web = Server.createWebServer(args.toArray(new String[0])).start();
                log.info("H2 Console started at http://127.0.0.1:{}/ (allowOthers={})", port, allowOthers);
            } catch (SQLException e) {
                log.warn("H2 Console start failed: {}. Retrying with minimal args…", e.toString());
                try {
                    // Fallback: minimal args
                    String[] minimal = new String[]{"-webPort", port};
                    log.info("Retrying H2 Console with args={}", Arrays.asList(minimal));
                    web = Server.createWebServer(minimal).start();
                    log.info("H2 Console started at http://127.0.0.1:{}/ (fallback)", port);
                } catch (SQLException e2) {
                    log.warn("Failed to start H2 Console (fallback): {}", e2.toString());
                }
            }
        }
    }

    /** Stop console. We only call this on application exit. */
    public static void stop() {
        synchronized (LOCK) {
            if (web != null) {
                try { web.stop(); log.info("H2 Console stopped."); }
                catch (Exception ignored) {}
                finally { web = null; }
            }
        }
    }

    private H2ConsoleServer() {}
}
