package dev.angularlite;

import java.util.List;
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider;

public final class Bundles implements TextMateBundleProvider {
    public List<PluginBundle> getBundles() {
        com.intellij.openapi.diagnostic.Logger.getInstance(Bundles.class).info("Loading Angular Lite grammars: " + Paths.plugin().resolve("textmate"));
        return List.of(new PluginBundle("Angular Lite", Paths.plugin().resolve("textmate")));
    }
}
