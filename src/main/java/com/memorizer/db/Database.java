package com.memorizer.db;

import com.memorizer.app.Config;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * H2 embedded DB helper. Uses MVStore file engine.
 * Path example: ./data/memo -> files memo.mv.db / memo.trace.db
 */
public final class Database {
    private static final Logger log = LoggerFactory.getLogger(Database.class);
    private static Connection conn;

    public static synchronized void start() {
        if (conn != null) return;
        String type = Config.get("app.db.type", "h2");
        if (!"h2".equalsIgnoreCase(type)) {
            throw new IllegalStateException("Stage A uses H2 by default. Other types will be added later.");
        }
        String basePath = Config.get("app.db.path", "./data/memo");

        try {
            // Ensure data directory exists
            Path base = Paths.get(basePath).toAbsolutePath();
            Path dir = base.getParent() != null ? base.getParent() : base;
            Files.createDirectories(dir);

            // H2 1.4.200 URL (MVStore). DO NOT use MULTI_THREADED here.
            String url = "jdbc:h2:file:" + base.toString() +
                    ";MODE=PostgreSQL" +
                    ";AUTO_SERVER=TRUE" +
                    ";DB_CLOSE_ON_EXIT=FALSE" +
                    ";DB_CLOSE_DELAY=-1";

            log.info("Opening H2 at url={}", url);

            conn = DriverManager.getConnection(url, "sa", "");

            // Run Flyway migrations (classpath:db/migration)
            Flyway.configure()
                    .dataSource(url, "sa", "")
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load()
                    .migrate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to open H2 connection", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare H2 directory", e);
        }
    }

    public static synchronized Connection get() {
        if (conn == null) start();
        return conn;
    }

    public static synchronized void stop() {
        if (conn != null) {
            try { conn.close(); } catch (Exception ignored) {}
            conn = null;
        }
    }

    private Database() {}
}
