package com.memorizer.app;

import com.memorizer.ui.MainStage;
import com.memorizer.ui.StealthStage;

/** Tiny app context for wiring UI references. */
public final class AppContext {
    private static StealthStage stealth;
    private static MainStage main;

    static void setStealth(StealthStage s){ stealth = s; }
    static void setMain(MainStage m){ main = m; }

    public static StealthStage getStealth(){ return stealth; }
    public static MainStage getMain(){ return main; }

    private AppContext(){}
}
