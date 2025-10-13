package com.memorizer.app;

import java.security.SecureRandom;
import java.util.Base64;

/** Simple in-memory pairing token manager (dev/local use). */
public final class PairingManager {
    private static final PairingManager INSTANCE = new PairingManager();
    private volatile String token;
    private volatile long issuedAt;
    private volatile long expiresAt;

    public static PairingManager get() { return INSTANCE; }

    private PairingManager() {}

    public synchronized String getOrCreateToken() {
        long now = System.currentTimeMillis();
        if (token == null || now >= expiresAt) {
            token = generate();
            issuedAt = now;
            expiresAt = now + 15 * 60_000L; // 15 minutes
        }
        return token;
    }

    public synchronized boolean verify(String t) {
        if (t == null) return false;
        long now = System.currentTimeMillis();
        return token != null && now < expiresAt && token.equals(t);
    }

    public long getIssuedAt() { return issuedAt; }
    public long getExpiresAt() { return expiresAt; }

    private static String generate() {
        byte[] buf = new byte[24];
        new SecureRandom().nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}

