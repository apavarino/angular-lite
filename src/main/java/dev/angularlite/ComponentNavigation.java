package dev.angularlite;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.redhat.devtools.lsp4ij.features.navigation.LSPGotoDeclarationHandler;

/** Ask Angular using the original document, independently of HTML PSI references. */
public final class ComponentNavigation implements GotoDeclarationHandler {
    @Override public PsiElement[] getGotoDeclarationTargets(PsiElement source, int offset, Editor editor) {
        NavigationDiagnostics.record("handler offset=" + offset + " injected=" + (editor.getDocument() instanceof DocumentWindow));
        var project = editor.getProject();
        if (project == null || project.isDisposed()) { NavigationDiagnostics.record("stop: no active project"); return null; }
        var location = hostLocation(editor.getDocument(), offset);
        if (location == null) { NavigationDiagnostics.record("stop: invalid fragment"); return null; }
        var document = location.document();
        offset = location.offset();
        var file = PsiDocumentManager.getInstance(project).getPsiFile(document);
        if (file == null || file.getVirtualFile() == null) { NavigationDiagnostics.record("stop: no host PSI file"); return null; }
        NavigationDiagnostics.record("host language=" + file.getLanguage().getID() + " type=" + file.getFileType().getName() + " offset=" + offset);
        String extension = file.getVirtualFile().getExtension();
        if (!"html".equalsIgnoreCase(extension) && !"ts".equalsIgnoreCase(extension)) return null;
        if ("ts".equalsIgnoreCase(extension) && !inSelector(document.getImmutableCharSequence(), offset)) return null;
        if (!new AngularMatcher().match(file.getVirtualFile(), project)) { NavigationDiagnostics.record("stop: no Angular root"); return null; }
        // Do not restrict navigation to tag names: HTML PSI does not necessarily
        // expose references for expressions or aliases inside Angular blocks.
        // Angular decides whether the identifier has a real declaration.
        offset = identifierOffset(document.getImmutableCharSequence(), offset);
        if (offset < 0) { NavigationDiagnostics.record("stop: caret is not on an identifier"); return null; }
        // The static LSP entry point bypasses semantic PSI's early "no reference"
        // return while preserving LSP4IJ's cancellation and location conversion.
        NavigationDiagnostics.record("request definition offset=" + offset);
        try {
            var targets = LSPGotoDeclarationHandler.getGotoDeclarationTargets(file, offset);
            NavigationDiagnostics.record("definition targets=" + targets.length);
            return targets.length == 0 ? null : targets;
        } catch (RuntimeException error) {
            NavigationDiagnostics.record("definition interrupted: " + error.getClass().getSimpleName());
            throw error;
        }
    }
    public record NavigationLocation(Document document, int offset) {}
    private static boolean inSelector(CharSequence text, int offset) {
        // The router verifies the actual Angular decorator with the TypeScript AST.
        int i = Math.min(offset, text.length()) - 1;
        while (i >= 0 && "'\"`\n\r".indexOf(text.charAt(i)) < 0) i--;
        if (i < 0 || Character.isWhitespace(text.charAt(i))) return false;
        i--;
        while (i >= 0 && Character.isWhitespace(text.charAt(i))) i--;
        if (i < 0 || text.charAt(i--) != ':') return false;
        while (i >= 0 && Character.isWhitespace(text.charAt(i))) i--;
        if (i >= 0 && "'\"".indexOf(text.charAt(i)) >= 0) i--;
        return i >= 7 && "selector".contentEquals(text.subSequence(i - 7, i + 1));
    }
    public static NavigationLocation hostLocation(Document document, int offset) {
        while (document instanceof DocumentWindow fragment) {
            if (!fragment.isValid()) return null;
            offset = fragment.injectedToHost(offset);
            document = fragment.getDelegate();
        }
        return new NavigationLocation(document, offset);
    }
    public static int identifierOffset(CharSequence text, int offset) {
        if (isNavigationPosition(text, offset)) return offset;
        int next = offset;
        while (next >= 0 && next < text.length() && "<[/(*".indexOf(text.charAt(next)) >= 0) next++;
        if (next != offset && isNavigationPosition(text, next)) return next;
        // Cmd+B is commonly invoked with the caret just after the identifier,
        // immediately before the closing quote of [input]='value'.
        if (offset > 0 && offset <= text.length() && isNavigationPosition(text, offset - 1)) return offset - 1;
        return -1;
    }
    public static boolean isNavigationPosition(CharSequence text, int offset) {
        if (offset < 0 || offset >= text.length()) return false;
        char value = text.charAt(offset);
        return nameChar(value) || value == '$';
    }
    public static boolean isTagName(CharSequence text, int offset) {
        if (offset < 0 || offset >= text.length() || !nameChar(text.charAt(offset))) return false;
        int start = offset;
        while (start > 0 && nameChar(text.charAt(start - 1))) start--;
        if (start > 0 && text.charAt(start - 1) == '/') start--;
        return start > 0 && text.charAt(start - 1) == '<';
    }
    private static boolean nameChar(char value) {
        return Character.isLetterOrDigit(value) || value == '-' || value == '_' || value == ':';
    }
}
