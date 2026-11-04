package dev.angularlite;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.regex.*;
import java.util.zip.*;

/** Managed tooling only: never installs packages into the user's project. */
public final class Bootstrap {
    public static final String NODE_VERSION = "24.22.2";
    private static final Map<String, String> HASHES = Map.of(
        "darwin-arm64", "aa9549349e47dffaa42dcf95d6e06ade8c4bdafc980979883b88767670df385a",
        "darwin-x64", "d5e336d588c45be0c37dbd52cc29895593009ca4997a77a1fd02815131ed0e52",
        "linux-arm64", "95c264150aceb7f3437a29fd6984924f5f8d1511ea3cbd6c508b83f6c555dae2",
        "linux-x64", "361718c444e49af2ca5a110c83ba6a8901143f23ab285ebbef0341f391f482a9",
        "win-arm64", "f47968c213105077f64c6510f007632c0ca2574802a0b5067c327c49abe1c468",
        "win-x64", "ca1bff5d8ead13c9f2ea404a176db46bd430704d5b5113734049fa365e7799e2");
    private static final Pattern VERSION = Pattern.compile("\"version\"\\s*:\\s*\"(\\d+\\.\\d+\\.\\d+)\"");
    public record Tooling(Path node, Path runtimes) {}

    public static String platform(String os, String arch) throws IOException {
        String system = os.toLowerCase(Locale.ROOT);
        String cpu = arch.toLowerCase(Locale.ROOT);
        String target = system.contains("mac") || system.contains("darwin") ? "darwin" : system.contains("win") ? "win" : system.contains("linux") ? "linux" : "unknown";
        String architecture = Set.of("aarch64", "arm64").contains(cpu) ? "arm64" : Set.of("amd64", "x86_64", "x64").contains(cpu) ? "x64" : "unknown";
        String result = target + "-" + architecture;
        if (!HASHES.containsKey(result)) throw new IOException("Système non pris en charge : " + os + " / " + arch);
        return result;
    }

    public static String angularVersion(Path project) throws IOException {
        for (Path p = project.toAbsolutePath().normalize(); p != null; p = p.getParent()) {
            Path manifest = p.resolve("node_modules/@angular/core/package.json");
            if (Files.isRegularFile(manifest)) {
                Matcher matcher = VERSION.matcher(Files.readString(manifest));
                if (matcher.find()) return matcher.group(1);
                throw new IOException("Version Angular stable introuvable dans " + manifest);
            }
        }
        throw new IOException("Dépendances Angular absentes. Installez les dépendances du projet puis rouvrez le fichier.");
    }

    public static Tooling ensure(Path project, Path cache, Path runtimes, Path plugin,
                                 Consumer<String> status, BooleanSupplier cancelled) throws Exception {
        String version = angularVersion(project);
        Path node = ensureNode(cache, status, cancelled);
        Path runtime = runtimes.resolve(version);
        Files.createDirectories(runtime);
        withLock(runtime.resolve(".install.lock"), cancelled, () -> {
            Path ready = runtime.resolve(".angular-lite-ready");
            if (Files.isRegularFile(ready) && Files.readString(ready).equals(version)
                && Files.isRegularFile(runtime.resolve("node_modules/@angular/language-server/index.js"))
                && Files.isRegularFile(runtime.resolve("node_modules/typescript-language-server/lib/cli.mjs"))) return;
            status.accept("Installation des moteurs Angular " + version + " et TypeScript…");
            Path log = runtime.resolve("installation.log");
            boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
            Path nodeHome = windows ? node.getParent() : node.getParent().getParent();
            Path npm = nodeHome.resolve(windows ? "node_modules/npm/bin/npm-cli.js" : "lib/node_modules/npm/bin/npm-cli.js");
            ProcessBuilder builder = new ProcessBuilder(node.toString(), plugin.resolve("setup.mjs").toString(),
                "--angular", version, "--runtimes", runtimes.toString());
            // A stable, accessible working directory prevents uv_cwd on macOS.
            builder.directory(runtime.toFile());
            builder.environment().put("ANGULAR_LITE_NPM_CLI", npm.toString());
            builder.environment().put("PATH", node.getParent() + File.pathSeparator + builder.environment().getOrDefault("PATH", ""));
            run(builder, log, Duration.ofMinutes(8), cancelled);
            if (!Files.isRegularFile(runtime.resolve("node_modules/@angular/language-server/index.js")))
                throw new IOException("Installation incomplète. Voir " + log);
            Files.writeString(ready, version);
            status.accept("Angular Lite est prêt : Angular " + version + ".");
        });
        return new Tooling(node, runtimes);
    }

    public static Path ensureNode(Path cache, Consumer<String> status, BooleanSupplier cancelled) throws Exception {
        String target = platform(System.getProperty("os.name"), System.getProperty("os.arch"));
        String name = "node-v" + NODE_VERSION + "-" + target;
        boolean windows = target.startsWith("win-");
        Path base = cache.resolve("node");
        Path home = base.resolve(name);
        Path executable = home.resolve(windows ? "node.exe" : "bin/node");
        Files.createDirectories(base);
        withLock(base.resolve(".install.lock"), cancelled, () -> {
            if (Files.isRegularFile(home.resolve(".angular-lite-ready")) && Files.isRegularFile(executable)) return;
            status.accept("Préparation de Node.js pour Angular Lite (premier démarrage)…");
            String filename = name + (windows ? ".zip" : ".tar.gz");
            Path staging = Files.createTempDirectory(base, "download-");
            try {
                Path archive = staging.resolve(filename);
                download(URI.create("https://nodejs.org/dist/v" + NODE_VERSION + "/" + filename), archive, cancelled);
                verifyHash(archive, HASHES.get(target));
                if (windows) unzip(archive, staging, cancelled);
                else run(new ProcessBuilder("/usr/bin/tar", "-xzf", archive.toString(), "-C", staging.toString()),
                    staging.resolve("extract.log"), Duration.ofMinutes(2), cancelled);
                Path unpacked = staging.resolve(name);
                if (!Files.isRegularFile(unpacked.resolve(windows ? "node.exe" : "bin/node")))
                    throw new IOException("Archive Node.js incomplète");
                // Keep an interrupted previous install for diagnosis; never delete user files.
                if (Files.exists(home)) Files.move(home, base.resolve(name + "-incomplete-" + UUID.randomUUID()));
                Files.move(unpacked, home);
                Files.writeString(home.resolve(".angular-lite-ready"), NODE_VERSION);
            } finally {
                // Only remove this invocation's dedicated staging directory.
                if (staging.toAbsolutePath().normalize().getParent().equals(base.toAbsolutePath().normalize())) {
                    try (var entries = Files.walk(staging)) {
                        for (Path path : entries.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                    }
                }
            }
        });
        return executable;
    }

    private static void download(URI uri, Path output, BooleanSupplier cancelled) throws Exception {
        URLConnection connection = uri.toURL().openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(output)) {
            byte[] buffer = new byte[65536];
            int length;
            while ((length = in.read(buffer)) >= 0) { check(cancelled); out.write(buffer, 0, length); }
        }
    }

    public static void verifyHash(Path file, String expected) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536]; int length;
            while ((length = stream.read(buffer)) >= 0) digest.update(buffer, 0, length);
        }
        if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected))
            throw new IOException("Le téléchargement Node.js est invalide (SHA-256). Réessayez le démarrage du serveur.");
    }

    private static void unzip(Path archive, Path destination, BooleanSupplier cancelled) throws Exception {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                check(cancelled);
                Path output = destination.resolve(entry.getName()).normalize();
                if (!output.startsWith(destination)) throw new IOException("Chemin ZIP invalide");
                if (entry.isDirectory()) Files.createDirectories(output);
                else { Files.createDirectories(output.getParent()); Files.copy(in, output); }
            }
        }
    }

    private static void run(ProcessBuilder builder, Path log, Duration timeout, BooleanSupplier cancelled) throws Exception {
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        Process process = builder.start();
        try {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                check(cancelled);
                if (System.nanoTime() > deadline) throw new IOException("Délai dépassé. Journal : " + log);
            }
            if (process.exitValue() != 0) throw new IOException("Installation échouée (code " + process.exitValue() + "). Journal : " + log);
        } finally {
            if (process.isAlive()) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
        }
    }

    private interface Work { void run() throws Exception; }
    private static void withLock(Path lock, BooleanSupplier cancelled, Work work) throws Exception {
        try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            while (true) {
                check(cancelled);
                FileLock acquired = null;
                try { acquired = channel.tryLock(); } catch (OverlappingFileLockException ignored) {}
                if (acquired != null) { try (FileLock held = acquired) { work.run(); } return; }
                Thread.sleep(200);
            }
        }
    }
    private static void check(BooleanSupplier cancelled) throws InterruptedException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedException("Préparation annulée");
    }
}
