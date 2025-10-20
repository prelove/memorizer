package com.memorizer.util;

import java.awt.*;

/** Utilities for querying screen/taskbar geometry to position windows. */
public final class ScreenUtil {
    public enum Edge { TOP, BOTTOM, LEFT, RIGHT, NONE }

    public static class Rect {
        public int x, y, w, h;
        public Rect(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
    }

    public static class TaskbarInfo {
        public final Rect rect;
        public final Edge edge;
        public final GraphicsDevice device;
        public TaskbarInfo(Rect rect, Edge edge, GraphicsDevice device) { this.rect = rect; this.edge = edge; this.device = device; }
    }

    /** Best-guess active device: pointer screen; fallback to default. */
    public static GraphicsDevice activeDevice() {
        try {
            PointerInfo pi = MouseInfo.getPointerInfo();
            if (pi != null) {
                GraphicsDevice dev = pi.getDevice();
                if (dev != null) return dev;
                Point p = pi.getLocation();
                for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                    if (gd.getDefaultConfiguration().getBounds().contains(p)) return gd;
                }
            }
        } catch (Throwable ignored) {}
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    }

    /** Visible rect (usable area) for the given device (screen bounds minus insets). */
    public static Rect visibleRect(GraphicsDevice gd) {
        Rectangle sb = gd.getDefaultConfiguration().getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gd.getDefaultConfiguration());
        int x = sb.x + insets.left;
        int y = sb.y + insets.top;
        int w = sb.width - (insets.left + insets.right);
        int h = sb.height - (insets.top + insets.bottom);
        if (w <= 0 || h <= 0) return new Rect(sb.x, sb.y, sb.width, sb.height);
        return new Rect(x, y, w, h);
    }

    /** Taskbar/dock rectangle for a given device with edge detection. */
    public static TaskbarInfo taskbarFor(GraphicsDevice gd) {
        Rectangle sb = gd.getDefaultConfiguration().getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gd.getDefaultConfiguration());

        if (insets.bottom > 0) return new TaskbarInfo(new Rect(sb.x, sb.y + sb.height - insets.bottom, sb.width, insets.bottom), Edge.BOTTOM, gd);
        if (insets.top    > 0) return new TaskbarInfo(new Rect(sb.x, sb.y, sb.width, insets.top), Edge.TOP, gd);
        if (insets.left   > 0) return new TaskbarInfo(new Rect(sb.x, sb.y, insets.left, sb.height), Edge.LEFT, gd);
        if (insets.right  > 0) return new TaskbarInfo(new Rect(sb.x + sb.width - insets.right, sb.y, insets.right, sb.height), Edge.RIGHT, gd);
        // Fallback: assume bottom bar of 40px
        return new TaskbarInfo(new Rect(sb.x, sb.y + sb.height - 40, sb.width, 40), Edge.BOTTOM, gd);
    }

    /** Taskbar rect for the primary screen (compat for existing calls). */
    public static Rect taskbarRect() {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        return taskbarFor(gd).rect;
    }

    /** Taskbar info for the screen under the pointer. */
    public static TaskbarInfo taskbarForPointer() {
        return taskbarFor(activeDevice());
    }

    private ScreenUtil(){}
}
