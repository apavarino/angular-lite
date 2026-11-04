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
        System.out.println("PASS decorators: @Component, @Input, @Output, @HostListener yellow; arguments and ordinary calls unchanged");
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
                var expected = AngularSyntaxHighlighter.isExpressionVariable(token.getScope())
                    ? com.intellij.openapi.editor.HighlighterColors.TEXT : AngularSyntaxHighlighter.VARIABLE;
                if (keys.length != 1 || keys[0] != expected)
                    throw new AssertionError("Unresolved expression must stay neutral, binding names remain purple: " + word);
                from += word.length();
            }
        }
        for (String word : List.of("literal", "42", "plain", "input")) {
            int offset = text.indexOf(word);
            var token = tokens.stream().filter(t -> t.getStartOffset() <= offset && t.getEndOffset() > offset).findFirst().orElseThrow();
            if (AngularSyntaxHighlighter.isAngularVariable(token.getScope())) throw new AssertionError("Unexpected purple: " + word);
        }
        System.out.println("PASS " + language + ": bindings purple, expressions neutral pending resolution; strings, numbers and HTML unchanged");
    }
}
