package dev.angularlite;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.server.*;
import com.redhat.devtools.lsp4ij.client.features.*;
import com.intellij.psi.PsiFile;
import com.intellij.notification.*;
import java.nio.file.Path;

public final class Servers {
    private static StreamConnectionProvider provider(Project project, String kind) {
        Settings settings = Settings.get();
        return new OSProcessStreamConnectionProvider(new GeneralCommandLine(settings.node)) {
            private volatile boolean cancelled;
            private Notification progress;
            private void status(String message, NotificationType type) {
                if (progress != null) progress.expire();
                progress = NotificationGroupManager.getInstance().getNotificationGroup("Angular Lite")
                    .createNotification("Angular Lite", message, type);
                progress.notify(project);
            }
            @Override public void start() throws CannotStartProcessException {
                cancelled = false;
                try {
                    var roots = Paths.roots(project);
                    if (roots.isEmpty()) throw new java.io.IOException("Aucun projet Angular installé trouvé dans le projet ou ses sous-dossiers (8 niveaux maximum).");
                    String node = settings.node;
                    Path runtimes = Path.of(settings.runtimes).toAbsolutePath().normalize();
                    if (settings.automatic) {
                        for (Path root : roots) {
                        Bootstrap.Tooling tools = Bootstrap.ensure(root,
                            Path.of(System.getProperty("user.home"), ".angular-lite-idea"), runtimes, Paths.plugin(),
                            message -> status(message, NotificationType.INFORMATION), () -> cancelled || project.isDisposed());
                        node = tools.node().toString();
                        }
                    }
                    if (cancelled || project.isDisposed()) throw new InterruptedException("Démarrage annulé");
                    var command = new GeneralCommandLine(node, Paths.plugin().resolve("server/router.mjs").toString(), kind, runtimes.toString());
                    command.addParameters(roots.stream().map(Path::toString).toList());
                    setCommandLine(command.withWorkDirectory(project.getBasePath()));
                    super.start();
                    if (progress != null) progress.expire();
                } catch (Exception error) {
                    // LSP4IJ reports startup failures, including their cause.
                    if (progress != null) progress.expire();
                    if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                    throw new CannotStartProcessException(error);
                }
            }
            @Override public void stop() { cancelled = true; super.stop(); }
        };
    }
    public static final class Angular implements LanguageServerFactory {
        public StreamConnectionProvider createConnectionProvider(Project project) { return provider(project, "angular"); }
        public LSPClientFeatures createClientFeatures() {
            return new LSPClientFeatures().setCodeLensFeature(new LSPCodeLensFeature() {
                public boolean isSupported(PsiFile file) { return false; }
            });
        }
    }
    public static final class TypeScript implements LanguageServerFactory {
        public StreamConnectionProvider createConnectionProvider(Project project) { return provider(project, "typescript"); }
        public LSPClientFeatures createClientFeatures() {
            // TypeScript semantic tokens repaint decorator names after TextMate has
            // colored them yellow (the @ itself has no semantic token). Keep one
            // owner for TS colors; template resolution has its own annotator.
            return new LSPClientFeatures().setSemanticTokensFeature(new LSPSemanticTokensFeature() {
                @Override public boolean isSupported(PsiFile file) { return false; }
                @Override public boolean isSemanticTokensSupported(PsiFile file) { return false; }
            });
        }
    }
}
