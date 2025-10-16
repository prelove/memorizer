package com.memorizer.util;

import java.awt.*;

/** Utilities for querying screen/taskbar geometry to position windows. */
public final class ScreenUtil {
    public static class Rect { public int x,y,w,h; public Rect(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;} }

    /** Taskbar/dock rectangle of primary screen. */
    public static Rect taskbarRect() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        Rectangle sb = gd.getDefaultConfiguration().getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gd.getDefaultConfiguration());

        if (insets.bottom > 0) return new Rect(sb.x, sb.y + sb.height - insets.bottom, sb.width, insets.bottom);
        if (insets.top    > 0) return new Rect(sb.x, sb.y, sb.width, insets.top);
        if (insets.left   > 0) return new Rect(sb.x, sb.y, insets.left, sb.height);
        if (insets.right  > 0) return new Rect(sb.x + sb.width - insets.right, sb.y, insets.right, sb.height);
        return new Rect(sb.x, sb.y + sb.height - 40, sb.width, 40); // fallback
    }

    private ScreenUtil(){}
}
