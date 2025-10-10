package com.memorizer.app;

import com.memorizer.service.StudyService;
import com.memorizer.ui.StealthStage;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.*;

/** Scheduler with pause/resume/snooze/showNow controls. */
public class Scheduler {
    private static final Logger log = LoggerFactory.getLogger(Scheduler.class);

    private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "memorizer-scheduler");
        t.setDaemon(true);
        return t;
    });
    private final Random rnd = new Random();
    private final StudyService study;
    private final StealthStage stealth;

    private volatile boolean paused = false;
    private volatile ScheduledFuture<?> future;

    public Scheduler(StudyService study, StealthStage stealth) {
        this.study = study;
        this.stealth = stealth;
    }

    public synchronized void start() {
        if (future != null && !future.isCancelled()) return;
        long initialDelayMs = TimeUnit.SECONDS.toMillis(5);
        future = ses.schedule(this::tick, initialDelayMs, TimeUnit.MILLISECONDS);
        log.info("Scheduler started.");
    }

    public synchronized void stop() {
        if (future != null) future.cancel(true);
        ses.shutdownNow();
    }

    public void pause() {
        paused = true;
        log.info("Scheduler paused.");
    }

    public void resume() {
        paused = false;
        log.info("Scheduler resumed.");
    }

    public boolean isPaused() { return paused; }

    /** Snooze next tick by N minutes (from now). */
    public synchronized void snooze(int minutes) {
        if (future != null) future.cancel(true);
        future = ses.schedule(this::tick, minutes, TimeUnit.MINUTES);
        log.info("Scheduler snoozed {} min.", minutes);
    }

    /** Manual show regardless of paused state. */
    public void showNow() {
        java.util.Optional<StudyService.CardView> v = study.currentOrNextOrFallback();
        if (v.isPresent()) {
            StudyService.CardView cv = v.get();
            Platform.runLater(() -> {
                int batch = com.memorizer.app.Config.getInt("app.study.batch-size", 1);
                stealth.startBatch(batch);
                stealth.showCardView(cv);   // 注意：现在传的是 CardView
                stealth.showAndFocus();
            });
        } else {
            log.info("showNow: no cards to show.");
        }
    }

    private int deferBusyMinutes() {
        return com.memorizer.app.Config.getInt("app.study.defer-when-busy-minutes", 3);
    }
    
    private void tick() {
        long nextDelayMin = nextDelayMinutes();
        try {
            if (paused) {
                log.debug("tick skipped (paused).");
                return;
            }

            if (stealth.isSessionActive() || stealth.isShowing()) {
                int d = deferBusyMinutes();
                log.info("Busy (session active). Defer next tick by {} min.", d);
                nextDelayMin = d;
                return;
            }

            java.util.Optional<com.memorizer.service.StudyService.CardView> v = study.nextCard();
            boolean forceWhenEmpty = Boolean.parseBoolean(
                    com.memorizer.app.Config.get("app.study.force-show-when-empty", "true"));
            if (!v.isPresent() && forceWhenEmpty) v = study.currentOrNextOrFallback();

            if (v.isPresent()) {
                final com.memorizer.service.StudyService.CardView cv = v.get();
                final int batch = com.memorizer.app.Config.getInt("app.study.batch-size", 1);
                javafx.application.Platform.runLater(() -> {
                    stealth.startBatch(batch);
                    stealth.showCardView(cv);
                    stealth.showAndFocus();
                });
            } else {
                log.info("No cards to show (due/new empty{}).", forceWhenEmpty ? "" : ", fallback disabled");
            }
        } catch (Exception e) {
            log.warn("tick error: {}", e.toString());
        } finally {
            synchronized (this) {
                future = ses.schedule(this::tick, nextDelayMin, java.util.concurrent.TimeUnit.MINUTES);
            }
        }
    }

    private long nextDelayMinutes() {
        int min = Config.getInt("app.study.min-interval-minutes", 20);
        int max = Config.getInt("app.study.max-interval-minutes", 60);
        if (max < min) max = min;
        return min + rnd.nextInt(Math.max(1, (max - min) + 1));
    }
}
