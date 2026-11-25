// Sprout dev server — Java, no Node, no dependencies. Serves the built app and
// rebuilds it whenever a source file changes. Run it straight from source:
//
//   java DevServer.java   ->   http://localhost:8080   (java DevServer.java 8081 for another port)
//
// One-off production build instead:  mvn -Pprod package
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class DevServer {
    static final Path ROOT = Path.of("").toAbsolutePath();
    static final Path WEB = ROOT.resolve("demo/target/web");            // served files (build output)
    static final Path WEBAPP = ROOT.resolve("demo/src/main/webapp");    // static page + data
    static final Path[] WATCH = {
        ROOT.resolve("demo/src/main/java"),
        ROOT.resolve("sprout-core/src/main/java"),
        WEBAPP,
    };
    // .wasm must be application/wasm — WebAssembly.compileStreaming rejects anything else.
    static final Map<String, String> TYPES = Map.of(
        ".html", "text/html", ".js", "text/javascript", ".wasm", "application/wasm",
        ".css", "text/css", ".txt", "text/plain", ".map", "application/json");

    // Live reload. The page polls this number and reloads itself when it moves.
    // ponytail: a 500ms poll instead of SSE or a websocket — no held connections,
    // no executor to configure, and the loop is one fetch. Cost is a request
    // every half second against a local server that does nothing else.
    static volatile long buildId = System.currentTimeMillis();

    // Injected into index.html as it is served, so the built index.html stays
    // free of it and production never sees a line of this.
    static final String LIVE_RELOAD = """
        <script>
          (function () {                                   // sprout dev server: live reload
            let seen = null;
            setInterval(() => fetch('/__sprout/build')
              .then(r => r.text())
              .then(id => { if (seen === null) { seen = id; } else if (id !== seen) { location.reload(); } })
              .catch(() => {}), 500);                      // server restarting — try again next tick
          })();
        </script>
        """;

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        build();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/__sprout/build", exchange -> {
            send(exchange, String.valueOf(buildId).getBytes(UTF_8), "text/plain");
        });
        server.createContext("/", exchange -> {
            String p = exchange.getRequestURI().getPath();
            Path file = WEB.resolve(("/".equals(p) ? "index.html" : p.substring(1))).normalize();
            if (!file.startsWith(WEB)) {                                 // no escaping the served dir
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            if (!Files.isRegularFile(file)) {
                // SPA fallback: /about and /data/3 are routes, not files. Anything
                // with an extension is a real asset, so a missing one stays a 404.
                if (file.getFileName().toString().contains(".")) {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                    return;
                }
                file = WEB.resolve("index.html");
            }
            byte[] body = Files.readAllBytes(file);
            if (file.getFileName().toString().equals("index.html")) {
                body = new String(body, UTF_8).replace("</body>", LIVE_RELOAD + "</body>").getBytes(UTF_8);
            }
            send(exchange, body, contentType(file));
        });
        server.start();
        System.out.println("Sprout dev server: http://localhost:" + port + "  (watching for changes)");
        watch();
    }

    static void send(HttpExchange exchange, byte[] body, String type) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        // no-store: without it a reload happily serves the classes.wasm the
        // build just replaced, and live reload appears to do nothing.
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    static String contentType(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "application/octet-stream"
                       : TYPES.getOrDefault(name.substring(dot), "application/octet-stream");
    }

    // ponytail: polls the newest mtime every 500ms instead of registering a
    // WatchService per directory — same result on a source tree this size,
    // a fraction of the code. Swap in WatchService if the tree gets huge.
    static void watch() throws Exception {
        long seen = newestChange();
        while (true) {
            Thread.sleep(500);
            long now = newestChange();
            if (now != seen) {
                build();
                seen = newestChange();          // ignore mtimes the build itself touched
            }
        }
    }

    static long newestChange() {
        long newest = 0;
        for (Path dir : WATCH) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(dir)) {
                newest = Math.max(newest, tree.filter(Files::isRegularFile)
                        .mapToLong(f -> f.toFile().lastModified()).max().orElse(0));
            } catch (IOException | RuntimeException e) {
                // a file vanished or was mid-write — next tick sees it
            }
        }
        return newest;
    }

    static void build() throws IOException, InterruptedException {
        System.out.print("\n> building... ");
        // mvn is a .cmd on Windows; ProcessBuilder won't find the extensionless name.
        String mvn = System.getProperty("os.name").startsWith("Windows") ? "mvn.cmd" : "mvn";
        Process p = new ProcessBuilder(mvn, "-q", "-pl", "demo", "-am", "process-classes")
                .directory(ROOT.toFile()).inheritIO().start();
        if (p.waitFor() == 0) {
            buildId = System.currentTimeMillis();      // the open page is polling this — it reloads itself
            System.out.println("+ built — the page reloads itself");
        } else {
            // Deliberately not bumping buildId: reloading into the previous
            // build is only confusing. The error is on screen in the terminal.
            System.out.println("x build failed (see errors above) — fix and save again");
        }
    }
}
