import dev.angularlite.Bootstrap;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class BootstrapCheck {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath();
        if (!Bootstrap.platform("Mac OS X", "aarch64").equals("darwin-arm64")) throw new AssertionError();
        if (!Bootstrap.platform("Mac OS X", "x86_64").equals("darwin-x64")) throw new AssertionError();
        if (!Bootstrap.platform("Windows 11", "amd64").equals("win-x64")) throw new AssertionError();
        try { Bootstrap.platform("Mac OS X", "ppc"); throw new AssertionError("unsupported architecture"); } catch(java.io.IOException expected) {}
        Path cache=Files.createTempDirectory(root.resolve("build"),"automatic cache ");
        Path altered=cache.resolve("altered.zip"); Files.writeString(altered,"bad download");
        try { Bootstrap.verifyHash(altered,"0".repeat(64)); throw new AssertionError("checksum accepted"); } catch(java.io.IOException expected) {}
        AtomicInteger installs=new AtomicInteger();
        var messages = new java.util.function.Consumer<String>() {
            public void accept(String s) { System.out.println(s); if(s.startsWith("Installation des moteurs")) installs.incrementAndGet(); }
        };
        Path runtimes=cache.resolve("runtimes");
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Bootstrap.Tooling> job=()->Bootstrap.ensure(root.resolve("demo"),cache,runtimes,root,messages,()->false);
            var first=pool.submit(job); var second=pool.submit(job);
            var one=first.get(10,TimeUnit.MINUTES); var two=second.get(10,TimeUnit.MINUTES);
            if (!one.node().equals(two.node()) || installs.get()!=1) throw new AssertionError("duplicate installation");
            int code=new ProcessBuilder(one.node().toString(),"--version").inheritIO().start().waitFor();
            if(code!=0) throw new AssertionError("managed Node does not start");
            Bootstrap.ensure(root.resolve("demo"),cache,runtimes,root,s->{throw new AssertionError("cache reused should not install");},()->false);
            try { Bootstrap.ensure(root.resolve("demo"),cache,runtimes,root,messages,()->true); throw new AssertionError("cancellation ignored"); }
            catch(InterruptedException expected) {}
            Files.writeString(root.resolve("build/automatic-test-runtime.txt"),runtimes.toString());
            Files.writeString(root.resolve("build/automatic-test-node.txt"),one.node().toString());
        }
        System.out.println("PASS automatic bootstrap: fresh cache, paths with spaces, concurrent servers, warm cache, cancellation, checksum, platform selection");
    }
}
