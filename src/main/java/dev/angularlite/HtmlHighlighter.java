package dev.angularlite;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateEditorHighlighterProvider;

public final class HtmlHighlighter implements EditorHighlighterProvider {
    public EditorHighlighter getEditorHighlighter(Project project, FileType type, VirtualFile file, EditorColorsScheme scheme) {
        if (project != null && file != null && ("html".equalsIgnoreCase(file.getExtension()) || "ts".equalsIgnoreCase(file.getExtension()))
            && new AngularMatcher().match(file, project)) {
            var syntax = SyntaxHighlighterFactory.getSyntaxHighlighter(org.jetbrains.plugins.textmate.TextMateFileType.INSTANCE, project, file);
            if (syntax != null) return new LexerEditorHighlighter(new AngularSyntaxHighlighter(syntax), scheme) {
                @Override protected com.intellij.openapi.editor.ex.util.DataStorage createStorage() {
                    return new org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateLexerDataStorage();
                }
            };
        }
        if (type == org.jetbrains.plugins.textmate.TextMateFileType.INSTANCE)
            return new TextMateEditorHighlighterProvider().getEditorHighlighter(project, type, file, scheme);
        return new LexerEditorHighlighter(SyntaxHighlighterFactory.getSyntaxHighlighter(type, project, file), scheme);
    }
}
