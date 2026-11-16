package dev.angularlite;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

/** Color only Angular tokens, preserving the original grammar and other colors. */
public final class AngularSyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey COMPONENT = XmlHighlighterColors.HTML_CUSTOM_TAG_NAME;
    private static TextAttributesKey key(String name, TextAttributesKey fallback) {
        return TextAttributesKey.createTextAttributesKey(name, fallback);
    }
    private static TextAttributesKey typeScript(String name, TextAttributesKey fallback) {
        return key("TS." + name, key("JS." + name, fallback));
    }
    public static final TextAttributesKey VARIABLE = key("NG.VARIABLE", typeScript("LOCAL_VARIABLE", DefaultLanguageHighlighterColors.LOCAL_VARIABLE));
    static final TextAttributesKey FIELD = typeScript("INSTANCE_MEMBER_VARIABLE", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    static final TextAttributesKey METHOD = typeScript("INSTANCE_MEMBER_FUNCTION", DefaultLanguageHighlighterColors.INSTANCE_METHOD);
    static final TextAttributesKey SIGNAL = key("NG.SIGNAL", METHOD);
    public static final TextAttributesKey DECORATOR = typeScript("DECORATOR", DefaultLanguageHighlighterColors.METADATA);
    private static final TextAttributesKey PROPERTY = key("NG.PROPERTY_BINDING_ATTR_NAME", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    private static final TextAttributesKey EVENT = key("NG.EVENT_BINDING_ATTR_NAME", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    private static final TextAttributesKey TWO_WAY = key("NG.BANANA_BINDING_ATTR_NAME", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    private static final TextAttributesKey TEMPLATE = key("NG.TEMPLATE_BINDINGS_ATTR_NAME", DefaultLanguageHighlighterColors.STATIC_FIELD);
    private static final TextAttributesKey BLOCK = key("NG.BLOCK_NAME", DefaultLanguageHighlighterColors.KEYWORD);
    private static final TextAttributesKey BRACES = key("NG.BLOCK_BRACES", DefaultLanguageHighlighterColors.BRACES);
    private static final TextAttributesKey DELIMITER = key("NG.SCRIPT_DELIMITERS", DefaultLanguageHighlighterColors.SEMICOLON);
    private final SyntaxHighlighter delegate;
    public AngularSyntaxHighlighter(SyntaxHighlighter delegate) { this.delegate = delegate; }
    @Override public Lexer getHighlightingLexer() { return delegate.getHighlightingLexer(); }
    @Override public TextAttributesKey[] getTokenHighlights(IElementType token) {
        if (token instanceof TextMateElementType textMate) {
            String scope = textMate.getScope().toString();
            TextAttributesKey key = templateKey(scope);
            if (key != null) return new TextAttributesKey[]{key};
        }
        if (token instanceof TextMateElementType textMate && isDecorator(textMate.getScope()))
            return new TextAttributesKey[]{DECORATOR};
        if (token instanceof TextMateElementType textMate && isExpressionVariable(textMate.getScope()))
            return new TextAttributesKey[]{com.intellij.openapi.editor.HighlighterColors.TEXT};
        if (token instanceof TextMateElementType textMate && isAngularVariable(textMate.getScope()))
            return new TextAttributesKey[]{VARIABLE};
        return delegate.getTokenHighlights(token);
    }
    private static TextAttributesKey templateKey(String scope) {
        if (scope.endsWith("entity.name.tag.html"))
            return scope.contains("meta.tag.custom.") ? COMPONENT : XmlHighlighterColors.HTML_TAG_NAME;
        if (scope.contains("entity.other.ng-binding-name.")) {
            if (scope.contains("meta.ng-binding.two-way.html")) return TWO_WAY;
            if (scope.contains("meta.ng-binding.event.html")) return EVENT;
            if (scope.contains("meta.ng-binding.template.html")) return TEMPLATE;
            if (scope.contains("meta.ng-binding.property.html")) return PROPERTY;
        }
        if (scope.endsWith("keyword.control.block.transition.ng") || scope.endsWith("keyword.control.block.kind.ng")) return BLOCK;
        if (scope.endsWith("punctuation.definition.block.ng")) return BRACES;
        if (scope.endsWith("punctuation.definition.interpolation.ng")) return DELIMITER;
        if (scope.endsWith("variable.other.constant.ng")) return VARIABLE;
        return null;
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
            if (name.startsWith("variable.") || name.equals("entity.name.function.ts")) variable = true;
            if (name.contains("entity.other.ng-binding-name.")) return true;
        }
        return false;
    }
    public static boolean isExpressionVariable(TextMateScope scope) {
        return isAngularVariable(scope) && !scope.toString().contains("entity.other.ng-binding-name.");
    }
}
