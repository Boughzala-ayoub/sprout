package sprout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.teavm.jso.browser.History;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;

/**
 * Client-side router over the History API — real URLs ({@code /users/42}), not
 * {@code #/users/42}. Give it a {@code routes} prop mapping a pattern to a page:
 *
 * <pre>
 * Map&lt;String, Function&lt;Props, VNode&gt;&gt; routes = new LinkedHashMap&lt;&gt;();
 * routes.put("/", TodoPage::of);              // a page's own of(Props) fits as-is
 * routes.put("/todos/:id", TodoDetail::of);   // ":id" arrives as a prop
 * routes.put("*", NotFound::of);              // fallback
 * </pre>
 *
 * <p>Patterns match segment by segment. {@code :name} captures one segment into
 * the page's {@link Props}; a trailing {@code *} matches the rest of the path.
 * The most specific matching pattern wins, whatever order the map is in.
 *
 * <p>Optional props:
 * <ul>
 *   <li>{@code guard} — a {@code Function<String, String>}: given the path being
 *       entered, return {@code null} to allow it or another path to redirect to.
 *       This is where an auth check goes.</li>
 *   <li>{@code base} — a path prefix this router serves, stripped before
 *       matching. A page mounted at {@code /settings/*} can render its own
 *       Router with {@code base = "/settings"} to nest routes under it.</li>
 * </ul>
 *
 * <p>Navigate with {@link VNode#link(String, String)} anchors (real {@code href}s,
 * so ctrl-click and "open in new tab" still work) or {@link #navigate(String)}.
 *
 * <p>Serving note: every route must return {@code index.html}. {@code DevServer}
 * does this; in production it is one rewrite rule in nginx/Netlify/S3.
 */
public class Router extends Component {

    /** Live routers' path States — pushState fires no event, so we tell them ourselves. */
    private static final List<State<String>> live = new ArrayList<>();

    private final State<String> path = new State<>(currentPath());
    private final EventListener<Event> onPopState = e -> path.set(currentPath());

    public Router() {
        live.add(path);
        Window.current().addEventListener("popstate", onPopState);      // back / forward buttons
    }

    public static VNode of(Props props) {
        return VNode.component(Router.class, Router::new, props);
    }

    @Override
    protected void onUnmount() {
        live.remove(path);
        Window.current().removeEventListener("popstate", onPopState);
    }

    @Override
    @SuppressWarnings("unchecked")
    public VNode render() {
        Map<String, Function<Props, VNode>> routes =
                (Map<String, Function<Props, VNode>>) props.get("routes");
        String current = path.get();

        Function<String, String> guard = (Function<String, String>) props.get("guard");
        if (guard != null) {
            String redirect = guard.apply(current);
            if (redirect != null && !redirect.equals(current)) {
                navigate(redirect);
                return VNode.text("");
            }
        }

        String base = (String) props.get("base");
        if (base != null && current.startsWith(base)) {
            current = current.substring(base.length());
            if (current.isEmpty()) {
                current = "/";
            }
        }

        String bestPattern = null;
        Props bestParams = null;
        int bestScore = -1;
        for (String pattern : routes.keySet()) {
            Props params = match(pattern, current);
            if (params != null && specificity(pattern) > bestScore) {
                bestScore = specificity(pattern);
                bestPattern = pattern;
                bestParams = params;
            }
        }
        if (bestPattern == null) {
            return VNode.el("p", "404 — no route for " + current);
        }
        return routes.get(bestPattern).apply(bestParams.set("path", current));
    }

    // ---- matching (pure — no DOM, so it unit-tests on the JVM) -------------

    /**
     * Matches {@code path} against {@code pattern}, returning the captured
     * {@code :params} as Props, or null if it does not match.
     */
    static Props match(String pattern, String path) {
        if ("*".equals(pattern)) {
            return new Props();
        }
        String[] want = segments(pattern);
        String[] got = segments(path);
        Props params = new Props();
        for (int i = 0; i < want.length; i++) {
            if ("*".equals(want[i])) {
                return params;                          // trailing * eats the rest
            }
            if (i >= got.length) {
                return null;
            }
            if (want[i].startsWith(":")) {
                params.set(want[i].substring(1), got[i]);
            } else if (!want[i].equals(got[i])) {
                return null;
            }
        }
        return want.length == got.length ? params : null;
    }

    /** Literal segments beat params beat wildcards, so "/todos/new" wins over "/todos/:id". */
    static int specificity(String pattern) {
        if ("*".equals(pattern)) {
            return 0;
        }
        int score = 1;
        for (String segment : segments(pattern)) {
            score += segment.startsWith(":") ? 1 : "*".equals(segment) ? 0 : 2;
        }
        return score;
    }

    private static String[] segments(String path) {
        String trimmed = path;
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);   // "/a/" and "/a" are one route
        }
        return trimmed.split("/");
    }

    // ---- navigation -------------------------------------------------------

    /** Go to {@code path} without reloading the page, and re-render the routers. */
    public static void navigate(String path) {
        History.current().pushState(null, "", path);
        for (State<String> p : new ArrayList<>(live)) {
            p.set(path);
        }
    }

    static String currentPath() {
        String p = Window.current().getLocation().getPathName();
        return p == null || p.isEmpty() ? "/" : p;
    }
}
