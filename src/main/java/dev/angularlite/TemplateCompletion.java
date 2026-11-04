package dev.angularlite;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.textmate.TextMateFileType;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;

/** Template syntax completion is local, including while the language server starts. */
public final class TemplateCompletion extends CompletionContributor {
    private record Snippet(String name, String body, int since) {}
    private static final List<Snippet> BLOCKS = List.of(
        new Snippet("if", "if (|) {\n  \n}", 17),
        new Snippet("else", "else {\n  |\n}", 17),
        new Snippet("else if", "else if (|) {\n  \n}", 17),
        new Snippet("for", "for (item of |; track item) {\n  \n}", 17),
        new Snippet("empty", "empty {\n  |\n}", 17),
        new Snippet("switch", "switch (|) {\n  \n}", 17),
        new Snippet("case", "case (|) {\n  \n}", 17),
        new Snippet("default", "default {\n  |\n}", 17),
        new Snippet("defer", "defer {\n  |\n}", 17),
        new Snippet("placeholder", "placeholder {\n  |\n}", 17),
        new Snippet("loading", "loading {\n  |\n}", 17),
        new Snippet("error", "error {\n  |\n}", 17),
        new Snippet("let", "let value = |;", 18));
    private static final List<Snippet> DIRECTIVES = List.of(
        new Snippet("ngIf", "ngIf=\"|\"", 0),
        new Snippet("ngFor", "ngFor=\"let item of |\"", 0),
        new Snippet("ngSwitchCase", "ngSwitchCase=\"|\"", 0),
        new Snippet("ngSwitchDefault", "ngSwitchDefault", 0));

    public record Context(char trigger, String prefix) {}
    public static Context context(PsiFile file, CharSequence text, int offset) {
        var virtual = file.getVirtualFile();
        if (virtual == null || !"html".equalsIgnoreCase(virtual.getExtension())
            || !new AngularMatcher().match(virtual, file.getProject())) return null;
        int start = offset;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
        if (start == 0) return null;
        int triggerOffset = start - 1;
        char trigger = text.charAt(triggerOffset);
        if (trigger != '@' && trigger != '*') return null;
        if (triggerOffset > 0 && (Character.isJavaIdentifierPart(text.charAt(triggerOffset - 1))
            || text.charAt(triggerOffset - 1) == '.')) return null;
        var syntax = SyntaxHighlighterFactory.getSyntaxHighlighter(TextMateFileType.INSTANCE, file.getProject(), virtual);
        if (syntax == null) return null;
        var lexer = syntax.getHighlightingLexer();
        lexer.start(text);
        while (lexer.getTokenType() != null && lexer.getTokenEnd() <= triggerOffset) lexer.advance();
        if (!(lexer.getTokenType() instanceof TextMateElementType token)) return null;
        String scope = token.getScope().toString();
        if (scope.contains("comment.") || scope.contains("string.") || scope.contains("expression.ng")
            || scope.contains("source.css") || scope.contains("source.js")) return null;
        boolean tag = scope.contains("meta.tag.");
        if ((trigger == '*') != tag) return null;
        return new Context(trigger, text.subSequence(start, offset).toString());
    }

    @Override public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
        var file = parameters.getOriginalFile();
        var context = context(file, parameters.getEditor().getDocument().getImmutableCharSequence(), parameters.getOffset());
        if (context == null) return;
        int major = angularMajor(file);
        var matches = result.withPrefixMatcher(context.prefix());
        java.util.Set<String> supplied = new java.util.HashSet<>();
        for (var snippet : context.trigger() == '@' ? BLOCKS : DIRECTIVES) {
            if (major < snippet.since()) continue;
            supplied.add(snippet.name());
            matches.addElement(LookupElementBuilder.create(snippet.name()).withTypeText("Angular", true)
                .withTailText(context.trigger() == '@' ? " — bloc" : " — directive structurelle", true)
                .withInsertHandler((insertion, item) -> {
                    String body = snippet.body();
                    int caret = body.indexOf('|');
                    body = body.replace("|", "");
                    insertion.getDocument().replaceString(insertion.getStartOffset(), insertion.getTailOffset(), body);
                    insertion.getEditor().getCaretModel().moveToOffset(insertion.getStartOffset() + (caret < 0 ? body.length() : caret));
                    insertion.setAddCompletionChar(false);
                }));
        }
        // The language server also supplies bare keywords. Keep one suggestion
        // per keyword so selecting it reliably executes our snippet insertion.
        result.runRemainingContributors(parameters, remaining -> {
            if (!supplied.contains(remaining.getLookupElement().getLookupString())) result.passResult(remaining);
        });
    }

    private static int angularMajor(PsiFile file) {
        var root = Paths.roots(file.getProject()).stream()
            .filter(path -> java.nio.file.Path.of(file.getVirtualFile().getPath()).startsWith(path))
            .max(java.util.Comparator.comparingInt(java.nio.file.Path::getNameCount)).orElse(null);
        if (root == null) return 0;
        try {
            var match = Pattern.compile("\"version\"\\s*:\\s*\"(\\d+)\\.").matcher(Files.readString(root.resolve("node_modules/@angular/core/package.json")));
            return match.find() ? Integer.parseInt(match.group(1)) : 0;
        } catch (java.io.IOException ignored) { return 0; }
    }

    public static final class Typed extends TypedHandlerDelegate {
        @Override public Result checkAutoPopup(char typed, Project project, Editor editor, PsiFile file) {
            if ((typed == '@' || typed == '*') && file.getVirtualFile() != null
                && "html".equalsIgnoreCase(file.getVirtualFile().getExtension())
                && new AngularMatcher().match(file.getVirtualFile(), project)) {
                AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
                return Result.STOP;
            }
            return Result.CONTINUE;
        }
    }
}
