package com.memorizer.app;

import java.io.*;
import java.util.Properties;

/**
 * Simple config loader. For Stage A we load only from classpath.
 * Later we can support external properties path override.
 */
public final class Config {
    private static final Properties PROPS = new Properties();
    private static final File PREFS_FILE = new File("data/prefs.properties");

    static {
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) PROPS.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }

        // Overlay with user preferences if present
        try {
            if (PREFS_FILE.exists()) {
                try (InputStream pin = new FileInputStream(PREFS_FILE)) {
                    Properties override = new Properties();
                    override.load(pin);
                    PROPS.putAll(override);
                }
            }
        } catch (IOException e) {
            // ignore overlay errors; proceed with defaults
        }
    }

    public static String get(String key, String def) {
        String v = PROPS.getProperty(key);
        if (v == null) return def;
        return sanitize(v);
    }

    public static int getInt(String key, int def) {
        try {
            String raw = PROPS.getProperty(key);
            if (raw == null) return def;
            String s = sanitize(raw);
            return Integer.parseInt(s);
        }
        catch (Exception e) { return def; }
    }

    public static boolean getBool(String key, boolean def) {
        String v = PROPS.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(sanitize(v));
    }

    /** Strip inline comments ("#" or ";") and trim. */
    private static String sanitize(String v) {
        if (v == null) return null;
        String s = v;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '#' || c == ';') { s = s.substring(0, i); break; }
        }
        return s.trim();
    }

    /** Update a property value at runtime and persist to prefs file. */
    public static void set(String key, String value) {
        if (key == null) return;
        if (value == null) PROPS.remove(key); else PROPS.setProperty(key, value);
        savePrefs();
    }

    /** Persist current properties override to data/prefs.properties (creates file if missing). */
    public static synchronized void savePrefs() {
        try {
            if (PREFS_FILE.getParentFile() != null) PREFS_FILE.getParentFile().mkdirs();
            try (OutputStream out = new FileOutputStream(PREFS_FILE)) {
                Properties p = new Properties();
                // To keep file small, only write keys differing from classpath defaults is complex; write all for simplicity
                p.putAll(PROPS);
                p.store(out, "Memorizer preferences");
            }
        } catch (IOException ignored) {}
    }

    /**
     * Parse and clamp the {@code app.ui.font-scale} value.
     * Valid range: 0.7 – 1.5.  Returns 1.0 on parse error or out-of-range input.
     */
    public static double getFontScale() {
        String raw = get("app.ui.font-scale", "1.0");
        try {
            double v = Double.parseDouble(raw.trim());
            return (v >= 0.7 && v <= 1.5) ? v : 1.0;
        } catch (Throwable ignored) { return 1.0; }
    }

    private Config() {}
}
