package dev.angularlite.qa;

import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.project.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import java.nio.file.*;
import java.util.concurrent.*;

public final class NavigationProbe implements StartupActivity, DumbAware {
    public void runActivity(Project project) {
        String output = System.getProperty("angularlite.qa.output");
        if (output == null) return;
        DumbService.getInstance(project).runWhenSmart(() -> {
            var file = LocalFileSystem.getInstance().refreshAndFindFileByPath(project.getBasePath() + "/app.html");
            var editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file), true);
            var psi = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            var servers = LanguageServiceAccessor.getInstance(project).getLanguageServers(psi, f -> true, f -> true);
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                StringBuilder report = new StringBuilder();
                try {
                    servers.get(120, TimeUnit.SECONDS);
                    for (String word : new String[]{"plainLabel", "countries[0]"}) {
                        int offset = editor.getDocument().getText().indexOf("='" + word) + 3;
                        PsiElement[] targets = null;
                        for (int attempt = 0; attempt < 10; attempt++) {
                            targets = ReadAction.compute(() -> GotoDeclarationAction.findAllTargetElements(project, editor, offset));
                            if (targets != null && targets.length > 0) break;
                            Thread.sleep(500);
                        }
                        report.append(word).append(" offset=").append(offset).append(" targets=").append(targets == null ? 0 : targets.length).append('\n');
                        if (targets != null) for (var target : targets) {
                            report.append(ReadAction.compute(() -> target.getContainingFile().getVirtualFile() + ":" + target.getTextOffset())).append('\n');
                        }
                        int start = editor.getDocument().getText().indexOf("='" + word) + 2;
                        int length = word.indexOf('[') >= 0 ? word.indexOf('[') : word.length();
                        final int endOffset = start + length;
                        var endTargets = ReadAction.compute(() -> GotoDeclarationAction.findAllTargetElements(project, editor, endOffset));
                        report.append("end-of-word ").append(word).append(" targets=").append(endTargets == null ? 0 : endTargets.length).append('\n');
                        var fragment = (com.intellij.injected.editor.DocumentWindow) java.lang.reflect.Proxy.newProxyInstance(
                            getClass().getClassLoader(), new Class[]{com.intellij.injected.editor.DocumentWindow.class}, (proxy, method, args) -> switch (method.getName()) {
                                case "isValid" -> true;
                                case "getDelegate" -> editor.getDocument();
                                case "injectedToHost" -> start + (Integer) args[0];
                                default -> throw new UnsupportedOperationException(method.getName());
                            });
                        var fragmentEditor = (Editor) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Editor.class},
                            (proxy, method, args) -> switch (method.getName()) {
                                case "getProject" -> project;
                                case "getDocument" -> fragment;
                                default -> throw new UnsupportedOperationException(method.getName());
                            });
                        var injectedTargets = ReadAction.compute(() -> new dev.angularlite.ComponentNavigation().getGotoDeclarationTargets(null, 1, fragmentEditor));
                        report.append("fragment ").append(word).append(" targets=").append(injectedTargets == null ? 0 : injectedTargets.length).append('\n');
                        if(targets == null || targets.length == 0 || endTargets == null || endTargets.length == 0 || injectedTargets == null || injectedTargets.length == 0)
                            throw new AssertionError("Missing navigation target for " + word);
                    }
                    report.append("PASS all navigation paths\n");
                } catch (Throwable error) {
                    report.append(error).append('\n');
                    for (var frame : error.getStackTrace()) report.append(frame).append('\n');
                }
                try { Files.writeString(Path.of(output), report.toString()); } catch (Exception error) { throw new RuntimeException(error); }
            });
        });
    }
}
