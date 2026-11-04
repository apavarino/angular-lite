import dev.angularlite.ProjectRoots;
import java.nio.file.*;
import java.util.List;

public class ProjectRootsCheck {
    static void installed(Path root) throws Exception {
        Path manifest = root.resolve("node_modules/@angular/core/package.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "{\"version\":\"22.2.4\"}");
    }
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("angular roots ");
        Path repo = Files.createDirectories(temp.resolve("repo"));
        check(ProjectRoots.find(repo).isEmpty(), "missing dependencies");
        Path front = repo.resolve("apps/front with spaces"); installed(front);
        installed(repo.resolve("node_modules/ignored")); installed(repo.resolve("dist/ignored"));
        check(ProjectRoots.find(repo).equals(List.of(front)), "nested frontend, spaces, ignored directories");
        Path source = Files.createDirectories(front.resolve("src/app"));
        check(ProjectRoots.find(source).equals(List.of(front)), "ancestor dependencies");
        Path other = repo.resolve("other"); installed(other);
        check(ProjectRoots.find(repo).containsAll(List.of(front, other)), "multiple frontends discovered");
        installed(repo);
        check(ProjectRoots.find(repo).containsAll(List.of(repo, front, other)), "hoisted and nested dependencies retained");
        Path deep = temp.resolve("deep");
        installed(deep.resolve("a/b/c/d/e/f/g/h/i"));
        check(ProjectRoots.find(deep).isEmpty(), "bounded scan");
    }
}
