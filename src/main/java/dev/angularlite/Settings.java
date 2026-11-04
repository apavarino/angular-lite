package dev.angularlite;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;

@State(name="AngularLiteSettings", storages=@Storage("angular-lite.xml"))
public final class Settings implements PersistentStateComponent<Settings> {
    public String node = "node";
    public boolean automatic = true;
    public String runtimes = System.getProperty("user.home") + "/.angular-lite-idea/runtimes";
    public static Settings get() { return ApplicationManager.getApplication().getService(Settings.class); }
    public Settings getState() { return this; }
    public void loadState(Settings state) { node = state.node; runtimes = state.runtimes; automatic = state.automatic; }
}
