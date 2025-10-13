package com.memorizer.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Dev-only helper to ensure a self-signed HTTPS keystore exists for the embedded web server.
 *
 * Strategy:
 * - Resolve keystore location under data/ (override via app.web.keystore.path)
 * - If missing, invoke JDK keytool to generate a PKCS12 keystore with a self-signed cert
 * - Password is configurable (app.web.keystore.pass), defaults to a local-dev value
 */
public final class CertificateManager {
    private static final Logger log = LoggerFactory.getLogger(CertificateManager.class);

    public static final String CFG_KEYSTORE_PATH = "app.web.keystore.path";
    public static final String CFG_KEYSTORE_PASS = "app.web.keystore.pass";
    public static final String CFG_KEY_ALIAS     = "app.web.keystore.alias";

    public static Path keystorePath() {
        String custom = Config.get(CFG_KEYSTORE_PATH, null);
        if (custom != null && !custom.trim().isEmpty()) {
            return Paths.get(custom.trim()).toAbsolutePath();
        }
        // default under data/
        return Paths.get("data", "keystore.p12").toAbsolutePath();
    }

    public static String keystorePassword() {
        // Dev default only; users may override in prefs
        return Config.get(CFG_KEYSTORE_PASS, "memorizer-dev-pass");
        
    }

    public static String keyAlias() {
        return Config.get(CFG_KEY_ALIAS, "memorizer");
    }

    /** Ensure a PKCS12 keystore exists; create one via keytool if missing. */
    public static synchronized Path ensureKeystore() {
        Path ks = keystorePath();
        try {
            Files.createDirectories(ks.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create keystore parent dir: " + ks, e);
        }
        if (Files.exists(ks)) {
            return ks;
        }
        String pass = keystorePassword();
        String alias = keyAlias();
        String dname = "CN=Desktop Memorizer Local";
        // Prefer keytool from JAVA_HOME, fall back to PATH
        String javaHome = System.getProperty("java.home");
        String kt = (javaHome == null) ? null : Paths.get(javaHome, "bin", isWindows() ? "keytool.exe" : "keytool").toString();
        if (kt == null || !Files.isReadable(Paths.get(kt))) {
            kt = isWindows() ? "keytool.exe" : "keytool";
        }
        ProcessBuilder pb = new ProcessBuilder(
                kt,
                "-genkeypair",
                "-alias", alias,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-storetype", "PKCS12",
                "-keystore", ks.toString(),
                "-validity", "3650",
                "-dname", dname,
                "-storepass", pass,
                "-keypass", pass
        );
        pb.redirectErrorStream(true);
        try {
            log.info("Generating self-signed dev keystore at {} (alias={})", ks, alias);
            Process p = pb.start();
            // Consume output (but ignore content) to avoid blocking on full buffer
            try (java.io.InputStream in = p.getInputStream()) {
                byte[] buf = new byte[1024];
                while (in.read(buf) != -1) { /* discard */ }
            }
            int code = p.waitFor();
            if (code != 0) throw new RuntimeException("keytool exited with code " + code);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to generate self-signed keystore using keytool", e);
        }
        if (!Files.exists(ks)) throw new RuntimeException("keytool reported success but keystore not found: " + ks);
        return ks;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    private CertificateManager() {}
}

