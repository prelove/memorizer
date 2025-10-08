package com.memorizer.util;

import java.awt.Desktop;
import java.net.URI;

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
