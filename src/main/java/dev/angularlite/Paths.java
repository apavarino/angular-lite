package dev.angularlite;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.List;
import com.intellij.openapi.util.Key;

public final class Paths {
    // User data outlives plugin unload: store platform/JDK values, never a
    // plugin-defined record that retains the old plugin class loader.
    private static final Key<com.intellij.openapi.util.Pair<Long, List<Path>>> ROOTS = Key.create("angularlite.roots");
    public static Path plugin() { return PluginManagerCore.getPlugin(PluginId.getId("dev.angularlite.idea")).getPluginPath(); }
    public static boolean angular(Project project) {
        return !roots(project).isEmpty();
    }
    public static List<Path> roots(Project project) {
        if (project.getBasePath() == null) return List.of();
        synchronized (project) {
            var cached = project.getUserData(ROOTS);
            long now = System.nanoTime();
            if (cached != null && now - cached.first < 5_000_000_000L) return cached.second;
            List<Path> roots = ProjectRoots.find(Path.of(project.getBasePath()));
            project.putUserData(ROOTS, com.intellij.openapi.util.Pair.create(now, roots));
            return roots;
        }
    }
}
