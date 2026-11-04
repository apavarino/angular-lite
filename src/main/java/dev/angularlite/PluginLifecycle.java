package dev.angularlite;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;

/** Open editors must be reanalysed when the plugin is enabled without restarting IDEA. */
public final class PluginLifecycle implements DynamicPluginListener {
    @Override public void pluginLoaded(IdeaPluginDescriptor descriptor) {
        if (!"dev.angularlite.idea".equals(descriptor.getPluginId().getIdString())) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            for (var editor : EditorFactory.getInstance().getAllEditors()) {
                var project = editor.getProject();
                var file = FileDocumentManager.getInstance().getFile(editor.getDocument());
                if (!editor.isDisposed() && project != null && !project.isDisposed() && file != null
                    && ("html".equalsIgnoreCase(file.getExtension()) || "ts".equalsIgnoreCase(file.getExtension()))
                    && editor instanceof EditorEx extended) {
                    extended.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file));
                }
            }
            for (var project : ProjectManager.getInstance().getOpenProjects()) {
                if (!project.isDisposed()) DaemonCodeAnalyzer.getInstance(project).restart();
            }
        });
    }
}
