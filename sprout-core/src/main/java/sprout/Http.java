package sprout;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.teavm.jso.ajax.XMLHttpRequest;

/**
 * Async HTTP, in the shape of {@code fetch}. Every call ends in one callback
 * that always runs — success, HTTP error, or network failure — because a UI that
 * only handles the happy path hangs on "Loading…" forever:
 *
 * <pre>
 * Http.get("/api/todos").send(res -&gt; {
 *     if (res.ok()) {
 *         todos.set(res.json().list());
 *     } else {
 *         error.set(res.error());
 *     }
 * });
 *
 * Http.post("/api/todos").json("{\"text\":\"buy milk\"}").send(res -&gt; ...);
 * </pre>
 *
 * <p>Update a {@link State} from the callback and the view re-renders. For the
 * usual loading / loaded / failed triple, {@link Resource} wraps this up.
 */
public final class Http {

    private final String method;
    private final String url;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private String body;

    private Http(String method, String url) {
        this.method = method;
        this.url = url;
    }

    public static Http get(String url)    { return new Http("GET", url); }
    public static Http post(String url)   { return new Http("POST", url); }
    public static Http put(String url)    { return new Http("PUT", url); }
    public static Http patch(String url)  { return new Http("PATCH", url); }
    public static Http delete(String url) { return new Http("DELETE", url); }

    public Http header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    /** Sends {@code body} as {@code application/json}. */
    public Http json(String jsonBody) {
        this.body = jsonBody;
        return header("Content-Type", "application/json");
    }

    /** Sends a {@link Json#obj()} body — the escaping is the browser's problem, not yours. */
    public Http json(Json.Builder jsonBody) {
        return json(jsonBody.toString());
    }

    public Http body(String raw) {
        this.body = raw;
        return this;
    }

    public void send(Consumer<Response> callback) {
        XMLHttpRequest xhr = XMLHttpRequest.create();
        xhr.open(method, url);
        for (Map.Entry<String, String> h : headers.entrySet()) {
            xhr.setRequestHeader(h.getKey(), h.getValue());
        }
        xhr.setOnReadyStateChange(() -> {
            if (xhr.getReadyState() != XMLHttpRequest.DONE) {
                return;
            }
            // status 0 means the request never completed: offline, CORS, DNS, timeout.
            int status = xhr.getStatus();
            String text = status == 0 ? "" : xhr.getResponseText();
            String failure = status == 0 ? "network error or request blocked (" + method + " " + url + ")"
                           : status >= 400 ? "HTTP " + status + " " + xhr.getStatusText()
                           : null;
            deliver(callback, new Response(status, text, failure));
        });
        try {
            if (body == null) {
                xhr.send();                               // not send(null): the null String conversion traps under WasmGC
            } else {
                xhr.send(body);
            }
        } catch (RuntimeException e) {
            deliver(callback, new Response(0, "", "could not send request: " + e));
        }
    }

    private static void deliver(Consumer<Response> callback, Response response) {
        try {
            callback.accept(response);
        } catch (RuntimeException e) {
            Sprout.reportError("Http callback", e);        // never let a bad callback kill the page
        }
    }

    /** What came back. Always inspect {@link #ok()} before {@link #text()}. */
    public static final class Response {
        private final int status;
        private final String text;
        private final String error;

        Response(int status, String text, String error) {
            this.status = status;
            this.text = text;
            this.error = error;
        }

        public boolean ok()     { return error == null; }
        public int status()     { return status; }
        public String text()    { return text; }
        /** Human-readable failure, or null when {@link #ok()}. */
        public String error()   { return error; }

        /** Parses the body as JSON. Returns an empty node if the body is not valid JSON. */
        public Json json()      { return Json.parse(text); }
    }
}
