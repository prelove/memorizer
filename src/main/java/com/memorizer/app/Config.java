package com.memorizer.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Simple config loader. For Stage A we load only from classpath.
 * Later we can support external properties path override.
 */
public final class Config {
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) PROPS.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
    }

    public static String get(String key, String def) {
        return PROPS.getProperty(key, def);
    }

    public static int getInt(String key, int def) {
        try { return Integer.parseInt(PROPS.getProperty(key, String.valueOf(def))); }
        catch (Exception e) { return def; }
    }

    public static boolean getBool(String key, boolean def) {
        String v = PROPS.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    private Config() {}
}
