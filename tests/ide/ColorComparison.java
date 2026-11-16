package dev.angularlite.qa;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.*;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.LocalFileSystem;
import java.nio.file.*;
import java.util.*;

/** Capture editor tokens and semantic overlays using the active profile's actual highlighter. */
public final class ColorComparison implements StartupActivity, DumbAware {
    public void runActivity(Project project) {
        var output = System.getProperty("angularlite.qa.output");
        if (output == null) return;
        DumbService.getInstance(project).runWhenSmart(() -> {
            var file = LocalFileSystem.getInstance().findFileByPath(project.getBasePath()+"/app.html");
            var editor = (EditorEx) FileEditorManager.getInstance(project)
                .openTextEditor(new OpenFileDescriptor(project,file),true);
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    String result = "";
                    for (int i=0; i<30; i++) {
                        Thread.sleep(1000);
                        String next = ReadAction.compute(() -> capture(editor,project));
                        boolean angularActive = next.contains("NG.PROPERTY_BINDING_ATTR_NAME");
                        if (i>=10 && angularActive && next.equals(result)) break;
                        result=next;
                    }
                    if (!result.contains("NG.PROPERTY_BINDING_ATTR_NAME"))
                        result = "Angular highlighting inactive. This capture is HTML only.\n" + result;
                    Files.writeString(Path.of(output),result);
                } catch (Exception error) { throw new RuntimeException(error); }
            });
        });
    }
    private static String capture(EditorEx editor, Project project) {
        var report=new StringBuilder("scheme=").append(editor.getColorsScheme().getName());
        for(String id:List.of("AngularJS","dev.angularlite.idea"))
            report.append(" ").append(id).append("=").append(PluginManagerCore.isLoaded(PluginId.getId(id)));
        report.append('\n');
        var document=editor.getDocument();
        String text=document.getText();
        for(String marker:List.of("<h1", "<demo-child", "[from]", "(saved)", "*ngIf", "@if", "{{", "criteria.from", "greet()", "title()", "{{ row")) {
            int pos=text.indexOf(marker);
            if(pos<0) throw new IllegalStateException("Missing fixture marker "+marker);
            int offset=pos+(marker.equals("{{ row")?3:"[<(*@".indexOf(marker.charAt(0))>=0?1:0);
            var token=editor.getHighlighter().createIterator(offset);
            var keys=Arrays.toString(token.getTextAttributesKeys());
            var color=token.getTextAttributes().getForegroundColor();
            report.append(marker).append("  ").append(keys).append("  ").append(color==null?"default":String.format("#%06X",color.getRGB()&0xffffff));
            DaemonCodeAnalyzerEx.processHighlights(document,project,HighlightSeverity.INFORMATION,offset,offset+1,h->{
                if(h.forcedTextAttributesKey!=null) {
                    var semantic = editor.getColorsScheme().getAttributes(h.forcedTextAttributesKey);
                    report.append(" +").append(h.forcedTextAttributesKey.getExternalName());
                    if (semantic != null && semantic.getForegroundColor() != null)
                        report.append(String.format(" #%06X",semantic.getForegroundColor().getRGB()&0xffffff));
                }
                return true;
            });
            report.append('\n');
        }
        return report.toString();
    }
}
