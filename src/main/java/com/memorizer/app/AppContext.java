package com.memorizer.app;

import com.memorizer.ui.MainStage;
import com.memorizer.ui.StealthStage;
import javafx.stage.Stage;

public final class AppContext {
    private static StealthStage stealth;
    private static MainStage main;
    private static Stage owner; // invisible owner for utility windows

    static void setStealth(StealthStage s){ stealth = s; }
    static void setMain(MainStage m){ main = m; }
    static void setOwner(Stage s){ owner = s; }

    public static StealthStage getStealth(){ return stealth; }
    public static MainStage getMain(){ return main; }
    public static Stage getOwner(){ return owner; }

    private AppContext(){}
}
