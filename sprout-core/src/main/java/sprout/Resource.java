package sprout;

import java.util.function.Function;

/**
 * The loading / loaded / failed triple that every page fetching data ends up
 * writing by hand. Hold one as a field, load it on mount, branch on it in
 * render:
 *
 * <pre>
 * private final Resource&lt;List&lt;Todo&gt;&gt; todos = new Resource&lt;&gt;();
 *
 * protected void onMount() {
 *     todos.load(Http.get("/api/todos"), res -&gt; parse(res.json()));
 * }
 *
 * public VNode render() {
 *     if (todos.loading()) return p("Loading…");
 *     if (todos.failed())  return p(todos.error()).cls("error");
 *     return ul(...todos.get()...);
 * }
 * </pre>
 *
 * <p>Reading any of the three in render subscribes to it, so the view updates
 * itself when the request lands.
 */
public final class Resource<T> {

    private final State<T> value = new State<>(null);
    private final State<String> error = new State<>(null);
    private final State<Boolean> inFlight = new State<>(false);

    /**
     * Runs {@code request}, mapping a successful response with {@code map}.
     * A failed request, or a {@code map} that throws, lands in {@link #error()}.
     */
    public void load(Http request, Function<Http.Response, T> map) {
        inFlight.set(true);
        error.set(null);
        request.send(response -> {
            if (!response.ok()) {
                fail(response.error());
                return;
            }
            try {
                value.set(map.apply(response));
                inFlight.set(false);
            } catch (RuntimeException e) {
                Sprout.reportError("Resource.load mapping", e);
                fail("could not read the response: " + e);
            }
        });
    }

    private void fail(String message) {
        error.set(message);
        inFlight.set(false);
    }

    /** The loaded value, or null while loading or after a failure. */
    public T get() {
        return value.get();
    }

    /**
     * True while there is nothing to show: a request is in flight, or none has
     * been started yet. A component's first render happens before its
     * {@code onMount}, so this must cover "not started" too — otherwise every
     * page would have to null-check the value as well.
     */
    public boolean loading() {
        return Boolean.TRUE.equals(inFlight.get()) || (!failed() && value.get() == null);
    }

    /** True while a request is in flight, even if a previous value is still showing. */
    public boolean refreshing() {
        return Boolean.TRUE.equals(inFlight.get());
    }

    public boolean failed() {
        return error.get() != null;
    }

    /** The failure message, or null. */
    public String error() {
        return error.get();
    }

    /** Drops the loaded value — call before reloading if the view should go back to "Loading…". */
    public void clear() {
        value.set(null);
        error.set(null);
    }
}
