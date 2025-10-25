package com.memorizer.web;

import com.memorizer.app.PairingManager;
import com.memorizer.app.WebServerManager;
import io.javalin.Javalin;

import java.util.HashMap;
import java.util.Map;

public final class PairingController {
    private PairingController() {}

    public static void register(Javalin app, WebServerManager mgr, String host, int port) {
        app.get("/api/pair/start", ctx -> {
            String t = PairingManager.get().getOrCreateToken();
            Map<String, Object> m = new HashMap<>();
            m.put("token", t);
            m.put("issuedAt", PairingManager.get().getIssuedAt());
            m.put("expiresAt", PairingManager.get().getExpiresAt());
            String scheme = mgr.isHttpsActive() ? "https" : "http";
            String base = String.format("%s://%s:%d", scheme, mgr.getHost()==null? host : mgr.getHost(), mgr.getPort()==0? port : mgr.getPort());
            m.put("server", base);
            ctx.json(m);
        });
        app.get("/api/pair/verify", ctx -> {
            String t = ctx.queryParam("token");
            boolean ok = PairingManager.get().verify(t);
            ctx.json(java.util.Collections.singletonMap("ok", ok));
        });
        app.get("/pair/verify", ctx -> {
            String t = ctx.queryParam("token");
            boolean ok = PairingManager.get().verify(t);
            ctx.json(java.util.Collections.singletonMap("ok", ok));
        });
        app.get("/pair", ctx -> {
            String t = PairingManager.get().getOrCreateToken();
            String scheme = mgr.isHttpsActive() ? "https" : "http";
            String base = String.format("%s://%s:%d", scheme, mgr.getHost()==null? host : mgr.getHost(), mgr.getPort()==0? port : mgr.getPort());
            String payloadJson = WebUtil.buildPairingPayload(mgr.getHost()==null? host : mgr.getHost(), mgr.getPort()==0? port : mgr.getPort(), t, mgr.isHttpsActive());
            String html = "<html><head><meta charset='utf-8'><title>Pair Mobile</title>"+
                    "<style>body{font-family:sans-serif;background:#1f2327;color:#f1f3f5} .row{margin:10px 0} .box{background:#2a2f34;padding:10px;border-radius:8px} input{width:100%;padding:8px;border-radius:6px;border:1px solid #121417;background:#1e2327;color:#e6e6e6} button{background:#3b82f6;color:#fff;border:none;border-radius:6px;padding:8px 10px;margin-left:8px} a{color:#9ecbff;text-decoration:none} .muted{color:#bdbdbd;font-size:12px} .pill{display:inline-block;padding:4px 8px;border-radius:999px;background:#2a2f34;margin-right:8px} .ok{color:#90ee90} .bad{color:#ff9aa2}</style>"+
                    "<script>function cp(id){var el=document.getElementById(id); el.select(); el.setSelectionRange(0,99999); try{navigator.clipboard.writeText(el.value);}catch(e){document.execCommand('copy');}} function st(){var sc=(window.isSecureContext? 'yes':'no'); var cam=(navigator.mediaDevices && navigator.mediaDevices.getUserMedia? 'yes':'no'); var scEl=document.getElementById('sc'); var cmEl=document.getElementById('cm'); if(scEl){ scEl.textContent=sc; scEl.className='pill '+(sc==='yes'?'ok':'bad'); } if(cmEl){ cmEl.textContent=cam; cmEl.className='pill '+(cam==='yes'?'ok':'bad'); }}</script>"+
                    "</head><body>"+
                    "<h2>Pair Mobile</h2>"+
                    "<div class='row'>Scan this QR in the mobile app:</div>"+
                    "<div class='row'><img src='/pair/qr.png' alt='QR' style='background:white;padding:8px;border-radius:8px;max-width:80vw;height:auto'/></div>"+
                    "<div class='row box'><div>Server URL</div><div style='display:flex;align-items:center;gap:8px'><input id='srv' value='"+base+"' readonly><button onclick=\"cp('srv')\">Copy</button><a href='"+base+"/pwa/' target='_blank'>Open PWA</a></div></div>"+
                    "<div class='row box'><div>Pairing Token</div><div style='display:flex;align-items:center;gap:8px'><input id='tok' value='"+t+"' readonly><button onclick=\"cp('tok')\">Copy</button></div></div>"+
                    "<div class='row box'><div>QR Payload (JSON)</div><div style='display:flex;align-items:center;gap:8px'><input id='jp' value='"+payloadJson+"' readonly><button onclick=\"cp('jp')\">Copy</button></div></div>"+
                    "<div class='row box'><div><b>Status</b></div><div>Secure Context: <span id='sc' class='pill'>...</span> Camera API: <span id='cm' class='pill'>...</span> <button onclick=\"st()\">Check</button></div></div>"+
                    "</body></html>";
            ctx.contentType("text/html; charset=utf-8").result(html);
        });
        app.get("/pair/qr.png", ctx -> {
            String t = PairingManager.get().getOrCreateToken();
            byte[] png = WebUtil.qrPng(WebUtil.buildPairingPayload(mgr.getHost()==null? host : mgr.getHost(), mgr.getPort()==0? port : mgr.getPort(), t, mgr.isHttpsActive()), 384);
            ctx.contentType("image/png").result(png);
        });
        app.get("/pair/ca.crt", ctx -> {
            try {
                java.nio.file.Path ca = com.memorizer.app.LocalCAService.caCertPath();
                if (!java.nio.file.Files.exists(ca)) { com.memorizer.app.LocalCAService.ensureCA(); }
                ca = com.memorizer.app.LocalCAService.caCertPath();
                if (!java.nio.file.Files.exists(ca)) { ctx.status(404).result("missing"); return; }
                ctx.contentType("application/x-x509-ca-cert");
                ctx.result(java.nio.file.Files.newInputStream(ca));
            } catch (Exception e) { ctx.status(500).result("error"); }
        });
    }
}

