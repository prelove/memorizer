package com.memorizer.app;

import javafx.application.Platform;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.memorizer.service.StudyService;
import com.memorizer.ui.StealthStage;

/** Simple scheduler: show stealth banner at randomized interval between min/max minutes. */
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

    public Scheduler(StudyService study, StealthStage stealth) {
        this.study = study;
        this.stealth = stealth;
    }

    public void start() {
        long initial = TimeUnit.SECONDS.toMillis(5); // first show quickly to verify
        ses.schedule(this::tick, initial, TimeUnit.MILLISECONDS);
        log.info("Scheduler started.");
    }

    private void tick() {
        try {
            java.util.Optional<StudyService.CardView> v = study.nextCard();
            if (v.isPresent()) {
                StudyService.CardView cv = v.get();
                Platform.runLater(() -> {
                    stealth.showCard(cv.front, cv.back);
                    stealth.show();
                });
            } else {
                log.info("No due/new cards. Will try later.");
            }
        } catch (Exception e) {
            log.warn("tick error: {}", e.toString());
        } finally {
            long delayMin = nextDelayMinutes();
            ses.schedule(this::tick, delayMin, TimeUnit.MINUTES);
        }
    }

    private long nextDelayMinutes() {
        int min = Config.getInt("app.study.min-interval-minutes", 20);
        int max = Config.getInt("app.study.max-interval-minutes", 60);
        if (max < min) max = min;
        return min + rnd.nextInt(Math.max(1, (max - min) + 1));
    }

    public void stop() {
        ses.shutdownNow();
    }
}
