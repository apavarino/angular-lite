package dev.angularlite;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Bounded breadth-first discovery; never descends into dependencies or links. */
public final class ProjectRoots {
    private static final Set<String> SKIP = Set.of("node_modules", ".git", ".idea", ".angular", "dist", "build", "coverage", "target", ".next", "vendor");
    public static List<Path> find(Path base) {
        base = base.toAbsolutePath().normalize();
        Path ancestor = null;
        for (Path p = base; p != null; p = p.getParent())
            if (installed(p)) { ancestor = p; break; }
        record Entry(Path path, int depth) {}
        ArrayDeque<Entry> queue = new ArrayDeque<>();
        List<Path> roots = new ArrayList<>();
        if (ancestor != null) roots.add(ancestor);
        queue.add(new Entry(base, 0));
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 10000 && !Thread.currentThread().isInterrupted()) {
            Entry entry = queue.remove();
            if (installed(entry.path()) && !roots.contains(entry.path())) roots.add(entry.path());
            if (entry.depth() >= 8) continue;
            try (var children = Files.list(entry.path())) {
                children.filter(p -> !SKIP.contains(p.getFileName().toString()))
                    .filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
                    .sorted().forEach(p -> queue.add(new Entry(p, entry.depth() + 1)));
            } catch (IOException | SecurityException ignored) { /* Inaccessible unrelated directory. */ }
        }
        return List.copyOf(roots);
    }
    private static boolean installed(Path root) {
        return Files.isRegularFile(root.resolve("node_modules/@angular/core/package.json"));
    }
}
