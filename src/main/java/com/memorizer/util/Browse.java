package com.memorizer.util;

import java.awt.Desktop;
import java.net.URI;

/** Small helper to open URLs in the default desktop browser (best-effort). */
public final class Browse {
    public static boolean open(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }
    private Browse(){}
}
