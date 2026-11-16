package dev.angularlite;

import com.intellij.lang.annotation.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.redhat.devtools.lsp4ij.*;
import org.eclipse.lsp4j.*;
import org.jetbrains.plugins.textmate.TextMateFileType;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType;
import java.util.*;
import java.util.concurrent.*;

/** External analysis: no network/process wait under the IDE read lock or UI thread. */
public final class ResolvedVariables extends ExternalAnnotator<ResolvedVariables.Input, ResolvedVariables.Result> {
    // PSI can survive a dynamic plugin unload. Do not retain this plugin's Result
    // class (and its class loader) in PSI user data.
    private static final Key<com.intellij.openapi.util.Trinity<Long, Long, Map<TextRange, TextAttributesKey>>> CACHE = Key.create("angularlite.resolved.variables");
    public record Input(PsiFile file, Document document, String text, long stamp, long projectStamp,
                        SyntaxHighlighter syntax, CompletableFuture<List<LanguageServerItem>> servers, Result cached) {}
    public record Result(long stamp, long projectStamp, Map<TextRange, TextAttributesKey> colors, boolean complete) {}
    @Override public Input collectInformation(PsiFile file) {
        var virtual = file.getVirtualFile();
        if (virtual == null || !("html".equals(virtual.getExtension()) || "ts".equals(virtual.getExtension()))
            || !new AngularMatcher().match(virtual, file.getProject())) return null;
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return null;
        var syntax = SyntaxHighlighterFactory.getSyntaxHighlighter(TextMateFileType.INSTANCE, file.getProject(), virtual);
        if (syntax == null) return null;
        var servers = LanguageServiceAccessor.getInstance(file.getProject()).getLanguageServers(file,
            features -> "angularlite.angular".equals(features.getServerWrapper().getServerDefinition().getId()),
            features -> features.getHoverFeature().isSupported(file));
        var stored = file.getUserData(CACHE);
        Result cached = stored == null ? null : new Result(stored.first, stored.second, stored.third, true);
        return new Input(file, document, document.getText(), document.getModificationStamp(),
            PsiModificationTracker.getInstance(file.getProject()).getModificationCount(), syntax, servers, cached);
    }
    @Override public Result doAnnotate(Input input) {
        if (input == null) return null;
        Result cached = input.cached();
        if (cached != null && cached.complete() && cached.stamp() == input.stamp() && cached.projectStamp() == input.projectStamp()) return cached;
        Map<TextRange, TextAttributesKey> colors = new HashMap<>();
        List<CompletableFuture<Hover>> pending = new ArrayList<>();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            var servers = await(input.servers(), input, deadline);
            if (servers.isEmpty()) return new Result(input.stamp(), input.projectStamp(), Map.of(), false);
            var server = servers.getFirst();
            var identifier = new TextDocumentIdentifier(server.getServerWrapper().toUriString(input.file().getVirtualFile()));
            var lexer = input.syntax().getHighlightingLexer();
            lexer.start(input.text());
            List<TextRange> candidates = new ArrayList<>();
            while (lexer.getTokenType() != null) {
                ProgressManager.checkCanceled();
                if (lexer.getTokenType() instanceof TextMateElementType token && AngularSyntaxHighlighter.isExpressionVariable(token.getScope())) {
                    String word = input.text().substring(lexer.getTokenStart(), lexer.getTokenEnd());
                    if (word.matches("[$\\p{L}_][$\\p{L}\\p{N}_]*")) candidates.add(new TextRange(lexer.getTokenStart(), lexer.getTokenEnd()));
                }
                lexer.advance();
            }
            int line = 0, lineStart = 0, scanned = 0;
            for (int base = 0; base < candidates.size(); base += 8) {
                pending.clear();
                var batch = candidates.subList(base, Math.min(base + 8, candidates.size()));
                for (TextRange range : batch) {
                    int offset = range.getStartOffset();
                    // UTF-16 offsets, as required by the language server.
                    while (scanned < offset) {
                        if (input.text().charAt(scanned++) == '\n') { line++; lineStart = scanned; }
                    }
                    pending.add(server.getTextDocumentService().hover(new HoverParams(identifier, new Position(line, offset - lineStart))));
                }
                for (int i = 0; i < pending.size(); i++) {
                    var color = hoverColor(await(pending.get(i), input, deadline));
                    if (color != null) colors.put(batch.get(i), color);
                }
            }
            return new Result(input.stamp(), input.projectStamp(), Map.copyOf(colors), true);
        } catch (com.intellij.openapi.progress.ProcessCanceledException error) {
            throw error;
        } catch (ExecutionException | TimeoutException | CancellationException error) {
            return new Result(input.stamp(), input.projectStamp(), Map.of(), false);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            for (var request : pending) if (!request.isDone()) request.cancel(true);
        }
    }
    private static TextAttributesKey hoverColor(Hover hover) {
        if (hover == null || hover.getContents() == null) return null;
        var contents = hover.getContents();
        if (contents.isRight()) return signatureColor(contents.getRight().getValue().replaceFirst("^```(?:typescript|ts)?\\s*", ""));
        for (var item : contents.getLeft()) {
            String value = item.isLeft() ? item.getLeft() : item.getRight().getValue();
            var color = signatureColor(value);
            if (color != null) return color;
        }
        return null;
    }
    private static final java.util.regex.Pattern SIGNAL_TYPE = java.util.regex.Pattern.compile(
        "^\\(property\\) [^:\\n]+:\\s*(?:Signal|WritableSignal|InputSignal|InputSignalWithTransform|ModelSignal)\\b");
    private static TextAttributesKey signatureColor(String signature) {
        if (SIGNAL_TYPE.matcher(signature).find()) return AngularSyntaxHighlighter.SIGNAL;
        if (signature.startsWith("(property) ")) return AngularSyntaxHighlighter.FIELD;
        if (signature.startsWith("(method) ") || signature.startsWith("(function) ")) return AngularSyntaxHighlighter.METHOD;
        if (signature.startsWith("(variable) ") || signature.startsWith("(parameter) ") || signature.startsWith("(reference) ")
            || signature.startsWith("(let) ")) return AngularSyntaxHighlighter.VARIABLE;
        return null;
    }
    private static <T> T await(CompletableFuture<T> future, Input input, long deadline) throws InterruptedException, ExecutionException, TimeoutException {
        for (;;) {
            ProgressManager.checkCanceled();
            if (input.file().getProject().isDisposed() || input.document().getModificationStamp() != input.stamp())
                throw new com.intellij.openapi.progress.ProcessCanceledException();
            if (PsiModificationTracker.getInstance(input.file().getProject()).getModificationCount() != input.projectStamp())
                throw new com.intellij.openapi.progress.ProcessCanceledException();
            if (System.nanoTime() >= deadline) throw new TimeoutException();
            try { return future.get(50, TimeUnit.MILLISECONDS); } catch (TimeoutException retry) { /* Check cancellation between waits. */ }
        }
    }
    @Override public void apply(PsiFile file, Result result, AnnotationHolder holder) {
        if (result == null) return;
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null || document.getModificationStamp() != result.stamp()
            || PsiModificationTracker.getInstance(file.getProject()).getModificationCount() != result.projectStamp()) return;
        if (result.complete()) file.putUserData(CACHE, com.intellij.openapi.util.Trinity.create(result.stamp(), result.projectStamp(), result.colors()));
        result.colors().forEach((range, color) -> holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(range).textAttributes(color).create());
    }
}
