package com.memorizer.web;

public final class WebUtil {
    private WebUtil() {}
    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;")
                .replace(">","&gt;").replace("\"","&quot;")
                .replace("'","&#39;");
    }
    /** Send a unified JSON error response: {"error": msg, "code": code} */
    public static void error(io.javalin.http.Context ctx, int status, String code, String msg) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("error", msg);
        m.put("code", code);
        ctx.status(status).json(m);
    }
    public static String buildPairingPayload(String host, int port, String token, boolean https) {
        String scheme = https ? "https" : "http";
        return String.format("{\"server\":\"%s://%s:%d\",\"token\":\"%s\"}", scheme, host, port, token);
    }
    public static byte[] qrPng(String content, int size) {
        try {
            com.google.zxing.qrcode.QRCodeWriter w = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix m = w.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size);
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int y=0;y<size;y++){
                for (int x=0;x<size;x++){
                    int v = m.get(x,y) ? 0x000000 : 0xFFFFFF;
                    img.setRGB(x,y, 0xFF000000 | (v & 0x00FFFFFF));
                }
            }
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("QR encode failed", e);
        }
    }
}

