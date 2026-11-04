package dev.angularlite;

import java.util.ArrayDeque;

/** Local, bounded trace without source text, symbol names, or filesystem paths. */
public final class NavigationDiagnostics {
    private static final ArrayDeque<String> events = new ArrayDeque<>();
    private NavigationDiagnostics() {}
    public static synchronized void record(String event) {
        if (events.size() == 30) events.removeFirst();
        events.addLast(java.time.LocalTime.now().withNano(0) + " " + event);
    }
    public static synchronized String report() {
        var app = com.intellij.openapi.application.ApplicationInfo.getInstance();
        return "Angular Lite 0.1.0 NAV5 (analyse des vues imbriquées)\nIDE " + app.getBuild().asString()
            + "\nOS " + System.getProperty("os.name") + "\n"
            + (events.isEmpty() ? "No navigation handler invocation recorded." : String.join("\n", events));
    }
}
