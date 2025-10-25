package com.memorizer.web;

import io.javalin.plugin.rendering.FileRenderer;
import io.javalin.plugin.rendering.JavalinRenderer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Minimal HTML renderer: loads classpath resources and replaces {{key}} tokens. */
public final class TemplateRenderer {
    private TemplateRenderer() {}

    public static void register() {
        JavalinRenderer.register(new FileRenderer() {
            @Override
            public String render(String filePath, Map<String, Object> model, io.javalin.http.Context context) {
                try {
                    String name = filePath;
                    if (name.startsWith("/")) name = name.substring(1);
                    if (!name.endsWith(".html")) name = name + ".html";
                    String tpl = readClasspath("/web/templates/" + name);
                    if (tpl == null) return "<pre>Template not found: " + name + "</pre>";
                    return substitute(tpl, model);
                } catch (Exception e) {
                    return "<pre>Template error: " + e.getMessage() + "</pre>";
                }
            }
        }, ".html");
    }

    private static String readClasspath(String path) {
        try (InputStream in = TemplateRenderer.class.getResourceAsStream(path)) {
            if (in == null) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String substitute(String tpl, Map<String, Object> model) {
        if (model == null || model.isEmpty()) return tpl;
        String out = tpl;
        for (Map.Entry<String,Object> e : model.entrySet()) {
            String k = e.getKey(); Object v = e.getValue();
            String val = v == null ? "" : String.valueOf(v);
            out = out.replace("{{"+k+"}}", val);
        }
        return out;
    }
}
