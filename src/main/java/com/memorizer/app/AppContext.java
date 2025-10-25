package com.memorizer.app;

import com.memorizer.ui.MainStage;
import com.memorizer.ui.StealthStage;
import javafx.stage.Stage;

/**
 * Global, minimal runtime context for the desktop app.
 * Holds references to primary UI stages and the system tray wiring so
 * non-UI services (scheduler, importers) can trigger UI actions safely.
 */
public final class AppContext {
    private static StealthStage stealth;
    private static MainStage main;
    private static Stage owner; // invisible owner for utility windows
    private static com.memorizer.app.TrayManager tray;
    private static com.memorizer.service.StudyService study;
    private static com.memorizer.service.PlanService plan;

    /** Attach the stealth stage (always-on-top mini window). */
    static void setStealth(StealthStage s){ stealth = s; }
    /** Attach the main stage (full window). */
    static void setMain(MainStage m){ main = m; }
    /** Attach an invisible utility owner for dialogs/tool windows. */
    static void setOwner(Stage s){ owner = s; }
    /** Attach the tray manager. */
    static void setTray(com.memorizer.app.TrayManager t){ tray = t; }
    static void setStudy(com.memorizer.service.StudyService s){ study = s; }
    static void setPlan(com.memorizer.service.PlanService p){ plan = p; }

    /** Get the stealth stage. */
    public static StealthStage getStealth(){ return stealth; }
    /** Get the main stage. */
    public static MainStage getMain(){ return main; }
    /** Get the invisible utility owner. */
    public static Stage getOwner(){ return owner; }
    /** Get the tray manager. */
    public static com.memorizer.app.TrayManager getTray(){ return tray; }
    public static com.memorizer.service.StudyService getStudy(){ return study; }
    public static com.memorizer.service.PlanService getPlan(){ return plan; }

    private AppContext(){}
}
