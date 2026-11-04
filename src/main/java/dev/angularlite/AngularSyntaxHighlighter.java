package dev.angularlite;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.JBColor;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

/** Color only Angular tokens, preserving the original grammar and other colors. */
public final class AngularSyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey VARIABLE = TextAttributesKey.createTextAttributesKey(
        "ANGULAR_LITE_TEMPLATE_VARIABLE", new TextAttributes(new JBColor(0x7952A8, 0xC792EA), null, null, null, 0));
    public static final TextAttributesKey DECORATOR = TextAttributesKey.createTextAttributesKey(
        "ANGULAR_LITE_DECORATOR", new TextAttributes(new JBColor(0x8A6500, 0xE5C07B), null, null, null, 0));
    private final SyntaxHighlighter delegate;
    public AngularSyntaxHighlighter(SyntaxHighlighter delegate) { this.delegate = delegate; }
    @Override public Lexer getHighlightingLexer() { return delegate.getHighlightingLexer(); }
    @Override public TextAttributesKey[] getTokenHighlights(IElementType token) {
        if (token instanceof TextMateElementType textMate && isDecorator(textMate.getScope()))
            return new TextAttributesKey[]{DECORATOR};
        if (token instanceof TextMateElementType textMate && isExpressionVariable(textMate.getScope()))
            return new TextAttributesKey[]{com.intellij.openapi.editor.HighlighterColors.TEXT};
        if (token instanceof TextMateElementType textMate && isAngularVariable(textMate.getScope()))
            return new TextAttributesKey[]{VARIABLE};
        return delegate.getTokenHighlights(token);
    }
    public static boolean isDecorator(TextMateScope scope) {
        if (scope == null) return false;
        String[] names = scope.toString().trim().split("\\s+");
        String leaf = names[names.length - 1];
        if (leaf.equals("punctuation.decorator.ts")) return true;
        if (!leaf.equals("entity.name.function.ts") && !leaf.startsWith("variable.")) return false;
        for (int i = names.length - 2; i >= 0; i--) {
            String name = names[i];
            if (name.equals("meta.decorator.ts")) return true;
            if (!name.equals("meta.function-call.ts")) return false;
        }
        return false;
    }
    public static boolean isAngularVariable(TextMateScope scope) {
        boolean variable = false;
        if (scope == null) return false;
        String[] names = scope.toString().trim().split("\\s+");
        for (int i = names.length - 1; i >= 0; i--) {
            String name = names[i];
            // Inner punctuation, literals and comments must keep their normal colors.
            if (name.startsWith("string.") || name.startsWith("comment.") || name.startsWith("punctuation.")
                || name.startsWith("constant.") || name.startsWith("keyword.")) return false;
            if (name.equals("expression.ng") || name.equals("meta.expression.ng")
                || name.equals("control.block.expression.ng")) return variable;
            if (name.startsWith("variable.")) variable = true;
            if (name.contains("entity.other.ng-binding-name.")) return true;
        }
        return false;
    }
    public static boolean isExpressionVariable(TextMateScope scope) {
        return isAngularVariable(scope) && !scope.toString().contains("entity.other.ng-binding-name.");
    }
}
