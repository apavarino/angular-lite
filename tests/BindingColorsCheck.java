import java.nio.file.Path;
import java.util.*;
import dev.angularlite.AngularSyntaxHighlighter;
import org.jetbrains.plugins.textmate.bundles.*;
import org.jetbrains.plugins.textmate.plist.*;
import org.jetbrains.plugins.textmate.language.*;
import org.jetbrains.plugins.textmate.language.syntax.*;
import org.jetbrains.plugins.textmate.language.syntax.lexer.*;
import org.jetbrains.plugins.textmate.language.syntax.selector.*;
import com.intellij.textmate.joni.JoniRegexFactory;

public class BindingColorsCheck {
    public static void main(String[] args) {
        var builder = new TextMateSyntaxTableBuilder(new TextMateInterner() {
            public String intern(String s) { return s.intern(); }
            public void clear() {}
        });
        var bundle = VSCBundleReaderKt.readVSCBundle(new JsonPlistReader(), new TextMateNioResourceReader(Path.of(args[0])));
        var grammars = bundle.readGrammars().iterator();
        while (grammars.hasNext()) builder.addSyntax(grammars.next().getPlist().getValue());
        var table = builder.build();
        String html = "<input [(ngModel)]=\"criteria.name\" [label]=\"label\" [title]=\"'literal'\" [tabIndex]=\"42\" class=\"plain\">";
        check(table, "text.html.basic", html);
        check(table, "text.html.basic", "@if (visible) {\n {{ criteria.name }}\n <p>{{ criteria.name }}</p>\n" + html + "\n}\n");
        check(table, "source.ts", "@Component({template: `@if (visible) {\n {{ criteria.name }}\n <p>{{ criteria.name }}</p>\n" + html + "\n}`})\nclass App {}\n");
        check(table, "text.html.basic", "@if (visible) { @for (item of items; track item) { " + html + " } } @else { <p>empty</p> }");
        check(table, "text.html.basic", "@switch (kind) { @case (1) { " + html + " } @default { <p>other</p> } }");
        check(table, "text.html.basic", "<main>\n@if (visible) {\n <div>\n @for (row of rows; track row.id) {\n <section>\n" + html + "\n<p>{{ criteria.name }}</p></section>\n }\n </div>\n}\n</main>");
        check(table, "text.html.basic", "<div *ngIf=\"visible\"><section *ngFor=\"let row of rows\">" + html + "{{ criteria.name }}</section></div>");
        check(table, "text.html.basic", "@if (visible) { <div>" + html.replace("=", " = ") + "{{ criteria.name }}</div> }");
        check(table, "text.html.basic", "@if (criteria.name) {\n @if (criteria.name) { " + html + " }\n}");
        check(table, "source.ts", "@Component({template: `" + html + "`})\nclass App {}\n");
        checkDecorators(table);
        String template = "@if (ok) { <app-card (save)=\"save()\" *ngIf=\"ok\">{{ title() }}</app-card> }";
        checkTemplateKeys(table, "text.html.basic", template);
        checkTemplateKeys(table, "source.ts", "@Component({ template: `" + template + "` }) class App {}");
        String tags = "<section><app-card [label]=\"name\"></app-card><app-card /></section>";
        var matcher = new TextMateSyntaxMatcherImpl(new JoniRegexFactory(), new TextMateSelectorWeigherImpl());
        var lexer = new TextMateLexerCore(table.getLanguageDescriptor("text.html.basic"), matcher, 10000, false);
        var highlighter = new AngularSyntaxHighlighter(new com.intellij.openapi.fileTypes.PlainSyntaxHighlighter());
        lexer.init(tags, 0);
        int green = 0;
        while (lexer.getCurrentOffset() < tags.length()) for (var token : lexer.advanceLine(() -> {})) {
            String value = tags.substring(token.getStartOffset(), token.getEndOffset());
            var keys = highlighter.getTokenHighlights(new TextMateElementType(token.getScope()));
            boolean component = Arrays.asList(keys).contains(AngularSyntaxHighlighter.COMPONENT);
            if (component != value.equals("app-card")) throw new AssertionError("Component color: " + token.getScope());
            if (component) green++;
        }
        if (green != 3) throw new AssertionError("Expected three green tag names");
        System.out.println("PASS component colors on opening, closing and self-closing tags");
    }
    static void checkTemplateKeys(TextMateSyntaxTableCore table, String language, String text) {
        var expected = Map.of("if", "NG.BLOCK_NAME", "{", "NG.BLOCK_BRACES", "}", "NG.BLOCK_BRACES",
            "{{", "NG.SCRIPT_DELIMITERS", "}}", "NG.SCRIPT_DELIMITERS", "ngIf", "NG.TEMPLATE_BINDINGS_ATTR_NAME");
        var lexer = new TextMateLexerCore(table.getLanguageDescriptor(language),
            new TextMateSyntaxMatcherImpl(new JoniRegexFactory(), new TextMateSelectorWeigherImpl()), 10000, false);
        var highlighter = new AngularSyntaxHighlighter(new com.intellij.openapi.fileTypes.PlainSyntaxHighlighter());
        lexer.init(text, 0);
        Set<String> seen = new HashSet<>();
        while (lexer.getCurrentOffset() < text.length()) for (var token : lexer.advanceLine(() -> {})) {
            int start = token.getStartOffset();
            if (start < text.indexOf("@if") || start > text.lastIndexOf("</app-card> }") + "</app-card> ".length()) continue;
            String value = text.substring(start, token.getEndOffset());
            String key = value.equals("save") && start == text.indexOf("(save)") + 1 ? "NG.EVENT_BINDING_ATTR_NAME" : expected.get(value);
            if (key == null) continue;
            var keys = highlighter.getTokenHighlights(new TextMateElementType(token.getScope()));
            if (keys.length != 1 || !keys[0].getExternalName().equals(key)) throw new AssertionError(language + ": " + value + " expected " + key);
            seen.add(value);
        }
        if (!seen.containsAll(expected.keySet()) || !seen.contains("save")) throw new AssertionError("Missing tokens: " + seen);
    }
    static void checkDecorators(TextMateSyntaxTableCore table) {
        String text = "@Component({selector: 'app-test'})\nclass App {\n @Input() label = '';\n @Output() changed = new EventEmitter();\n @HostListener('click', ['$event']) onClick() {}\n}\n";
        var matcher = new TextMateSyntaxMatcherImpl(new JoniRegexFactory(), new TextMateSelectorWeigherImpl());
        var lexer = new TextMateLexerCore(table.getLanguageDescriptor("source.ts"), matcher, 10000, false);
        lexer.init(text, 0);
        int yellow = 0;
        while (lexer.getCurrentOffset() < text.length()) for (var token : lexer.advanceLine(() -> {})) {
            String value = text.substring(token.getStartOffset(), token.getEndOffset());
            boolean expected = Set.of("@", "Component", "Input", "Output", "HostListener").contains(value);
            boolean actual = AngularSyntaxHighlighter.isDecorator(token.getScope());
            if (expected != actual) throw new AssertionError("Decorator color: " + value + " -> " + token.getScope());
            if (actual) yellow++;
        }
        if (yellow != 8) throw new AssertionError("Expected 4 yellow decorator names and 4 @ signs");
        System.out.println("PASS decorators: @Component, @Input, @Output, @HostListener use the decorator style");
    }
    static void check(TextMateSyntaxTableCore table, String language, String text) {
        var matcher = new TextMateSyntaxMatcherImpl(new JoniRegexFactory(), new TextMateSelectorWeigherImpl());
        var lexer = new TextMateLexerCore(table.getLanguageDescriptor(language), matcher, 10000, false);
        lexer.init(text, 0);
        List<TextmateToken> tokens = new ArrayList<>();
        while (lexer.getCurrentOffset() < text.length()) tokens.addAll(lexer.advanceLine(() -> {}));
        for (String word : List.of("ngModel", "criteria", "name", "label")) {
            int from = 0;
            while ((from = text.indexOf(word, from)) >= 0) {
                final int offset = from;
                var token = tokens.stream().filter(t -> t.getStartOffset() <= offset && t.getEndOffset() > offset).findFirst().orElseThrow();
                if (!AngularSyntaxHighlighter.isAngularVariable(token.getScope()))
                    throw new AssertionError(language + " should color " + word + ": " + token.getScope());
                var highlighter = new AngularSyntaxHighlighter(new com.intellij.openapi.fileTypes.PlainSyntaxHighlighter());
                var keys = highlighter.getTokenHighlights(new TextMateElementType(token.getScope()));
                String expected = AngularSyntaxHighlighter.isExpressionVariable(token.getScope())
                    ? "TEXT" : word.equals("ngModel") ? "NG.BANANA_BINDING_ATTR_NAME" : "NG.PROPERTY_BINDING_ATTR_NAME";
                if (keys.length != 1 || !keys[0].getExternalName().equals(expected))
                    throw new AssertionError("Expected " + expected + " at " + word + ": " + Arrays.toString(keys));
                from += word.length();
            }
        }
        for (String word : List.of("literal", "42", "plain", "input")) {
            int offset = text.indexOf(word);
            var token = tokens.stream().filter(t -> t.getStartOffset() <= offset && t.getEndOffset() > offset).findFirst().orElseThrow();
            if (AngularSyntaxHighlighter.isAngularVariable(token.getScope())) throw new AssertionError("Unexpected purple: " + word);
        }
        System.out.println("PASS " + language + ": scheme binding keys and neutral unresolved expressions");
    }
}
