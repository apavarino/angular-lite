package dev.angularlite.qa;

import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.project.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.psi.*;
import com.intellij.ide.plugins.*;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Runs inside a real IDE, without a dependency on the plugin being reloaded. */
public final class IntegrationProbe implements StartupActivity, DumbAware {
    private static final PluginId ID = PluginId.getId("dev.angularlite.idea");
    private static void edt(Runnable action) { ApplicationManager.getApplication().invokeAndWait(action); }
    private static Class<?> pluginClass(String name) throws Exception {
        return PluginManagerCore.getPlugin(ID).getPluginClassLoader().loadClass("dev.angularlite." + name);
    }
    public void runActivity(Project project) {
        String output = System.getProperty("angularlite.qa.output");
        if (output == null) return;
        DumbService.getInstance(project).runWhenSmart(() -> {
            var file = LocalFileSystem.getInstance().refreshAndFindFileByPath(project.getBasePath() + "/app.html");
            var editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file), true);
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                StringBuilder report = new StringBuilder();
                String original = editor.getDocument().getText();
                try {
                    report.append("IDE ").append(ApplicationInfo.getInstance().getFullVersion()).append('\n');
                    for (String id : List.of("JavaScript", "AngularJS", "com.intellij.modules.ultimate")) {
                        if (PluginManagerCore.isLoaded(PluginId.getId(id))) throw new AssertionError("Paid plugin loaded: " + id);
                        report.append("DISABLED ").append(id).append('\n');
                    }
                    checkNavigation(project, editor, report);
                    checkColors(project, editor, report);
                    checkAutoPopup(project, editor, "", '@', "if", report);
                    checkAutoPopup(project, editor, "<div ", '*', "ngIf", report);
                    checkCompletion(project, editor, "@", "if", "@if () {\n  \n}", report);
                    checkCompletion(project, editor, "@f", "for", "@for (item of ; track item) {\n  \n}", report);
                    checkCompletion(project, editor, "<div *", "ngIf", "<div *ngIf=\"\"", report);
                    checkCompletion(project, editor, "<div *ng", "ngFor", "<div *ngFor=\"let item of \"", report);
                    setText(project, editor, original);
                    checkContexts(project, editor, report);
                    checkReload(project, editor, report);
                    checkNavigation(project, editor, report);
                    checkColors(project, editor, report);
                    checkAppliedHighlights(project, editor, report);
                    report.append("PASS ALL IDE CHECKS\n");
                } catch (Throwable error) {
                    report.append("FAIL ").append(error).append('\n');
                    try { report.append(pluginClass("NavigationDiagnostics").getMethod("report").invoke(null)).append('\n'); } catch (Exception ignored) {}
                    for (var frame : error.getStackTrace()) report.append(frame).append('\n');
                } finally {
                    setText(project, editor, original);
                    try { Files.writeString(Path.of(output), report.toString()); }
                    catch (Exception error) { throw new RuntimeException(error); }
                }
            });
        });
    }
    private static void setText(Project project, Editor editor, String text) {
        edt(() -> {
            LookupManager.getInstance(project).hideActiveLookup();
            WriteCommandAction.runWriteCommandAction(project, () -> editor.getDocument().setText(text));
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            editor.getCaretModel().moveToOffset(text.length());
        });
    }
    private static void checkNavigation(Project project, Editor editor, StringBuilder report) throws Exception {
        var psi = ReadAction.compute(() -> PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()));
        LanguageServiceAccessor.getInstance(project).getLanguageServers(psi, f -> true, f -> true).get(120, TimeUnit.SECONDS);
        String text = editor.getDocument().getText();
        for (String needle : List.of("criteria.from", "plainLabel", "countries[0]", "{{ count", "[label] = \"row", "{{ legacyItem", "{{ legacyIndex")) {
            int index = text.indexOf(needle);
            if (index < 0) throw new AssertionError("Missing marker " + needle);
            int offset = index + (needle.startsWith("{{") ? 4 : needle.startsWith("[label]") ? 12 : 1);
            PsiElement[] targets = null;
            for (int attempt = 0; attempt < 30; attempt++) {
                targets = ReadAction.nonBlocking(() -> GotoDeclarationAction.findAllTargetElements(project, editor, offset))
                    .inSmartMode(project).executeSynchronously();
                if (targets != null && targets.length > 0) break;
                Thread.sleep(300);
            }
            if (targets == null || targets.length == 0) throw new AssertionError("Navigation " + needle);
            report.append("PASS navigation ").append(needle).append('\n');
        }
    }
    @SuppressWarnings("unchecked")
    private static void checkColors(Project project, Editor editor, StringBuilder report) throws Exception {
        var psi = ReadAction.compute(() -> PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()));
        Class<?> type = pluginClass("ResolvedVariables");
        var annotator = type.getConstructor().newInstance();
        var collect = type.getMethod("collectInformation", PsiFile.class);
        var annotate = Arrays.stream(type.getMethods()).filter(m -> m.getName().equals("doAnnotate") && !m.isBridge()).findFirst().orElseThrow();
        List<TextRange> ranges = List.of();
        for (int attempt=0; attempt<15; attempt++) {
            try {
                var input = ReadAction.compute(() -> {
                    try { return collect.invoke(annotator, psi); } catch (Exception e) { throw new RuntimeException(e); }
                });
                var result = annotate.invoke(annotator, input);
                if (result != null && (Boolean) result.getClass().getMethod("complete").invoke(result)) {
                    ranges = (List<TextRange>) result.getClass().getMethod("ranges").invoke(result);
                    break;
                }
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (!(e.getCause() instanceof com.intellij.openapi.progress.ProcessCanceledException)) throw e;
            }
            Thread.sleep(400);
        }
        String text = editor.getDocument().getText();
        for (String word : List.of("criteria", "plainLabel", "countries", "count", "row", "legacyItem", "legacyIndex")) {
            int from=0, checked=0;
            while ((from=text.indexOf(word,from))>=0) {
                int offset=from;
                if ((offset > 0 && Character.isJavaIdentifierPart(text.charAt(offset-1)))
                    || (offset+word.length()<text.length() && Character.isJavaIdentifierPart(text.charAt(offset+word.length())))) {
                    from+=word.length(); continue;
                }
                // Declarations in microsyntax and block headers are not expression references.
                String before=text.substring(Math.max(0,offset-8),offset);
                if (!before.endsWith("let ") && !before.endsWith("as ") && !before.endsWith("@for (")
                    && !text.substring(offset+word.length()).startsWith("s")) {
                    if (ranges.stream().noneMatch(range -> range.containsOffset(offset)))
                        throw new AssertionError("Unresolved color " + word + " at " + offset + " ranges=" + ranges.size());
                    checked++;
                }
                from+=word.length();
            }
            report.append("PASS resolved colors ").append(word).append(" refs=").append(checked).append('\n');
        }
    }
    private static void checkCompletion(Project project, Editor editor, String text, String label, String expected, StringBuilder report) throws Exception {
        setText(project, editor, text);
        edt(() -> new CodeCompletionHandlerBase(CompletionType.BASIC, true, false, false).invokeCompletion(project, editor));
        boolean[] selected={false};
        for(int i=0;i<40 && !selected[0];i++) {
            edt(() -> {
                var lookup=LookupManager.getActiveLookup(editor);
                if (lookup==null) return;
                for(var item:lookup.getItems()) if(item.getLookupString().equals(label)) {
                    ((LookupImpl)lookup).finishLookup(Lookup.NORMAL_SELECT_CHAR,item);
                    selected[0]=true;
                    break;
                }
            });
            if(!selected[0]) Thread.sleep(150);
        }
        String actual=editor.getDocument().getText();
        if(!expected.equals(actual)) throw new AssertionError("Completion " + text + " -> " + actual + " expected " + expected);
        report.append("PASS completion + insertion ").append(text).append(" -> ").append(label).append('\n');
    }
    private static void checkAutoPopup(Project project, Editor editor, String text, char typed, String label, StringBuilder report) throws Exception {
        setText(project, editor, text);
        edt(() -> com.intellij.openapi.editor.actionSystem.TypedAction.getInstance().actionPerformed(editor, typed,
            com.intellij.ide.DataManager.getInstance().getDataContext(editor.getContentComponent())));
        boolean[] found={false};
        for(int i=0;i<50 && !found[0];i++) {
            edt(() -> {
                var lookup=LookupManager.getActiveLookup(editor);
                found[0]=lookup!=null && lookup.getItems().stream().anyMatch(item->item.getLookupString().equals(label));
            });
            if(!found[0]) Thread.sleep(150);
        }
        if(!found[0]) throw new AssertionError("No automatic popup after " + typed);
        report.append("PASS automatic popup ").append(typed).append('\n');
    }
    private static void checkContexts(Project project, Editor editor, StringBuilder report) throws Exception {
        var psi = ReadAction.compute(() -> PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()));
        var method=pluginClass("TemplateCompletion").getMethod("context",PsiFile.class,CharSequence.class,int.class);
        for(String text:List.of("<!-- @", "<div title=\"@", "{{ value *", "contact@example", "<script>@")) {
            Object result=ReadAction.compute(() -> {try {return method.invoke(null,psi,text,text.length());}catch(Exception e){throw new RuntimeException(e);}});
            if(result!=null) throw new AssertionError("Unexpected snippet context " + text);
        }
        report.append("PASS no snippets in comments/strings/expressions/scripts/email\n");
    }
    private static void checkReload(Project project, Editor editor, StringBuilder report) throws Exception {
        String[] result={null};
        edt(() -> {
            var descriptor=(PluginMainDescriptor)PluginManagerCore.getPlugin(ID);
            String reason=DynamicPlugins.INSTANCE.validateCanUnloadWithoutRestart(descriptor);
            if(reason!=null) {result[0]=reason;return;}
            boolean unloaded=DynamicPlugins.INSTANCE.unloadPlugin(descriptor,new DynamicPlugins.UnloadPluginOptions().withDisable(true).withSave(false));
            if(!unloaded) {result[0]="Unload failed";return;}
            boolean loaded=DynamicPlugins.INSTANCE.loadPlugin(descriptor,project);
            if(!loaded) result[0]="Load failed";
        });
        if(result[0]!=null) throw new AssertionError("Dynamic reload: " + result[0]);
        Thread.sleep(1500);
        var psi=ReadAction.compute(() -> PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()));
        var type=pluginClass("HtmlInspectionSuppressor");
        var suppressor=type.getConstructor().newInstance();
        boolean suppressed=ReadAction.compute(() -> {
            try {return (Boolean)type.getMethod("isSuppressedFor",PsiElement.class,String.class).invoke(suppressor,psi,"HtmlUnknownTag");}
            catch(Exception e){throw new RuntimeException(e);}
        });
        if(!suppressed) throw new AssertionError("Unknown-tag suppression lost after reload");
        report.append("PASS actual dynamic unload/load and inspection suppression\n");
    }
    private static void checkAppliedHighlights(Project project, Editor editor, StringBuilder report) throws Exception {
        int[] colors={0}, unknown={0};
        for(int i=0;i<60;i++) {
            colors[0]=0; unknown[0]=0;
            ReadAction.run(() -> DaemonCodeAnalyzerEx.processHighlights(editor.getDocument(),project,HighlightSeverity.INFORMATION,0,editor.getDocument().getTextLength(), info -> {
                if(info.forcedTextAttributesKey!=null && "ANGULAR_LITE_TEMPLATE_VARIABLE".equals(info.forcedTextAttributesKey.getExternalName())) colors[0]++;
                if("HtmlUnknownTag".equals(info.getInspectionToolId())) unknown[0]++;
                return true;
            }));
            if(colors[0]>0 && unknown[0]==0) break;
            Thread.sleep(250);
        }
        if(colors[0]==0 || unknown[0]>0) throw new AssertionError("Applied highlights after reload: colors="+colors[0]+", unknown tags="+unknown[0]);
        report.append("PASS applied editor colors after reload: ").append(colors[0]).append("; no HtmlUnknownTag warnings\n");
    }
}
