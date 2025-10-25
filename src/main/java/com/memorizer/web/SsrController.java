package com.memorizer.web;

import com.memorizer.app.WebServerManager;
import io.javalin.Javalin;

import java.util.HashMap;
import java.util.Map;

public final class SsrController {
    private SsrController() {}

    public static void register(Javalin app, WebServerManager mgr) {
        // static assets
        app.get("/web/static/*", ctx -> {
            String rel = ctx.path().substring("/web/static/".length());
            if (rel.contains("..")) { ctx.status(400).result("bad"); return; }
            java.io.InputStream in = SsrController.class.getResourceAsStream("/web/static/" + rel);
            if (in == null) { ctx.status(404).result("not found"); return; }
            String ct = rel.endsWith(".css")?"text/css": rel.endsWith(".js")?"application/javascript":"text/plain";
            ctx.contentType(ct);
            ctx.result(in);
        });

        // home
        app.get("/web", ctx -> {
            StringBuilder content = new StringBuilder();
            content.append("<section class='grid'>")
                    .append("<a class='card' href='/web/decks'><h3>Decks</h3><p>Manage your decks.</p></a>")
                    .append("<a class='card' href='/web/notes'><h3>Browse</h3><p>Find and edit notes.</p></a>")
                    .append("<a class='card' href='/web/study'><h3>Study</h3><p>Review cards in the browser.</p></a>")
                    .append("</section>");
            Map<String,Object> m = new HashMap<>();
            m.put("title", "Memorizer Web");
            m.put("serverMode", mgr.isHttpsActive()?"https":"http");
            String flash = ctx.queryParam("msg");
            m.put("flash", flash==null?"":"<div class='toast'>"+WebUtil.escape(flash)+"</div>");
            m.put("content", content.toString());
            ctx.render("layout.html", m);
        });
    }
}

