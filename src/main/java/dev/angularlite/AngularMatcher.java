package dev.angularlite;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.AbstractDocumentMatcher;

public final class AngularMatcher extends AbstractDocumentMatcher {
    public boolean match(VirtualFile file, Project project) {
        String path = file.getPath();
        if (path.contains("/node_modules/") || path.contains("/dist/")) return false;
        var document = java.nio.file.Path.of(path).toAbsolutePath().normalize();
        return Paths.roots(project).stream().anyMatch(document::startsWith);
    }
}
