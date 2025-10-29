package com.memorizer.ui;

import com.memorizer.app.Config;
import javafx.animation.KeyFrame;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ExamplesViewManagerTest {

    @BeforeAll
    static void initFx() throws Exception {
        // Initialize JavaFX runtime via JFXPanel to be compatible across JavaFX versions
        new JFXPanel();
    }

    @Test
    void rollIntervalReadsFromConfig() throws Exception {
        Config.set("app.ui.examples.roll-interval-ms", "3500");
        VBox box = new VBox();
        ScrollPane sp = new ScrollPane();
        Label mini = new Label();
        ExamplesViewManager m = new ExamplesViewManager(box, sp, mini);
        Platform.runLater(() -> m.setExamples(Arrays.asList("a","b","c"), StealthStage.UIMode.NORMAL));
        Thread.sleep(200); // allow timeline creation

        Field f = ExamplesViewManager.class.getDeclaredField("roller");
        f.setAccessible(true);
        Object tl = f.get(m);
        assertNotNull(tl, "roller timeline should be created");
        javafx.animation.Timeline timeline = (javafx.animation.Timeline) tl;
        KeyFrame kf = timeline.getKeyFrames().get(0);
        assertEquals(3500, (int) kf.getTime().toMillis());
    }

    @Test
    void marqueeTimingRespondsToConfig() throws Exception {
        Label mini = new Label();
        mini.setText("This is a quite long example line to force marquee.");
        ExamplesViewManager m = new ExamplesViewManager(new VBox(), new ScrollPane(), mini);

        // Use a small per-px duration
        Config.set("app.ui.examples.marquee-ms-per-px", "10");
        Platform.runLater(() -> m.startMiniMarqueeOn(mini));
        Thread.sleep(200);
        Field f = ExamplesViewManager.class.getDeclaredField("marquee");
        f.setAccessible(true);
        javafx.animation.Timeline t1 = (javafx.animation.Timeline) f.get(m);
        double d1 = t1.getKeyFrames().get(1).getTime().toMillis();

        // Double per-px duration; expect longer animation
        Config.set("app.ui.examples.marquee-ms-per-px", "20");
        Platform.runLater(() -> m.startMiniMarqueeOn(mini));
        Thread.sleep(200);
        javafx.animation.Timeline t2 = (javafx.animation.Timeline) f.get(m);
        double d2 = t2.getKeyFrames().get(1).getTime().toMillis();

        assertTrue(d2 > d1, "marquee duration increases with ms/px config");
    }
}
