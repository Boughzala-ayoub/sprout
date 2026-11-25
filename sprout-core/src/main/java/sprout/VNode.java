package sprout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.teavm.jso.JSBody;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.html.HTMLInputElement;

/**
 * A virtual-DOM node: a cheap, immutable-ish description of UI. One of four
 * kinds — a host element ({@code div}), a text node, a mounted {@link Component},
 * or a fragment (a group with no visible wrapper).
 *
 * <p>The static methods are the render DSL. Import them:
 * <pre>import static sprout.VNode.*;</pre>
 */
public class VNode {
    static final int HOST = 0, TEXT = 1, COMPONENT = 2, FRAGMENT = 3;

    int kind;
    String tag;                                             // HOST
    String text;                                            // TEXT
    final Map<String, String> attrs = new LinkedHashMap<>();
    final Map<String, Consumer<Event>> handlers = new LinkedHashMap<>();
    final List<VNode> children = new ArrayList<>();
    String key;                                             // for list reconciliation
    Class<?> type;                                          // COMPONENT identity
    Supplier<Component> factory;                            // COMPONENT constructor
    Props props;                                            // COMPONENT props

    private VNode(int kind) {
        this.kind = kind;
    }

    // ---- factories --------------------------------------------------------

    public static VNode text(String value) {
        VNode n = new VNode(TEXT);
        n.text = value == null ? "" : value;
        return n;
    }

    /** A host element. Children may be VNodes, Strings, or Lists/arrays of them; nulls are skipped. */
    public static VNode el(String tag, Object... children) {
        VNode n = new VNode(HOST);
        n.tag = tag;
        for (Object c : children) {
            n.addChild(c);
        }
        return n;
    }

    /** A group with no visible wrapper (rendered via CSS display:contents). */
    public static VNode fragment(Object... children) {
        VNode n = new VNode(FRAGMENT);
        for (Object c : children) {
            n.addChild(c);
        }
        return n;
    }

    /** A component element. The reconciler owns the instance; this only describes it. */
    public static VNode component(Class<?> type, Supplier<Component> factory, Props props) {
        VNode n = new VNode(COMPONENT);
        n.type = type;
        n.factory = factory;
        n.props = props == null ? new Props() : props;
        return n;
    }

    private void addChild(Object c) {
        if (c == null || Boolean.FALSE.equals(c)) {
            return;                                         // React-style: null/false children are skipped
        }
        if (c instanceof VNode) {
            children.add((VNode) c);
        } else if (c instanceof String) {
            children.add(text((String) c));
        } else if (c instanceof List<?>) {
            for (Object o : (List<?>) c) {
                addChild(o);
            }
        } else if (c instanceof VNode[]) {
            for (VNode v : (VNode[]) c) {
                addChild(v);
            }
        } else {
            children.add(text(String.valueOf(c)));
        }
    }

    // ---- chainable builders ----------------------------------------------

    public VNode attr(String name, String value) { attrs.put(name, value); return this; }
    public VNode style(String value)             { attrs.put("style", value); return this; }
    public VNode id(String value)                { attrs.put("id", value); return this; }
    public VNode key(String value)               { this.key = value; return this; }

    /**
     * A presence attribute — {@code .attr("disabled", text.isEmpty())} adds it or
     * takes it away. Writing {@code .attr("disabled", "false")} would not: HTML
     * reads any value at all, including {@code "false"}, as true.
     */
    public VNode attr(String name, boolean present) {
        if (present) {
            attrs.put(name, "");
        } else {
            attrs.remove(name);
        }
        return this;
    }

    /**
     * Adds classes, skipping nulls and blanks and accumulating across calls — so
     * a conditional class needs no string surgery:
     * {@code .cls("todo", done ? "done" : null)}.
     */
    public VNode cls(String... values) {
        StringBuilder out = new StringBuilder(attrs.getOrDefault("class", ""));
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                out.append(out.length() == 0 ? "" : " ").append(value);
            }
        }
        if (out.length() > 0) {
            attrs.put("class", out.toString());
        }
        return this;
    }

    public VNode on(String event, Consumer<Event> handler) { handlers.put(event, handler); return this; }
    public VNode onClick(Runnable handler)                 { handlers.put("click", e -> handler.run()); return this; }
    public VNode onInput(Consumer<String> handler)         { handlers.put("input", e -> handler.accept(inputValue(e))); return this; }

    /** A checkbox's new state. Pair with {@code .attr("checked", flag)} for a controlled checkbox. */
    public VNode onChange(Consumer<Boolean> handler) {
        return on("change", e -> handler.accept(isChecked(e)));
    }

    /**
     * Submit handler, with {@code preventDefault} already applied. Wrapping
     * fields in a {@link #form} and using this is what makes the Enter key work —
     * a bare click handler on the button never sees it.
     */
    public VNode onSubmit(Runnable handler) {
        return on("submit", e -> {
            e.preventDefault();
            handler.run();
        });
    }

    private static String inputValue(Event e) {
        return ((HTMLInputElement) e.getTarget()).getValue();
    }

    // ---- element shortcuts ------------------------------------------------

    public static VNode div(Object... c)     { return el("div", c); }
    public static VNode span(Object... c)    { return el("span", c); }
    public static VNode button(Object... c)  { return el("button", c); }
    public static VNode input()              { return el("input"); }
    public static VNode input(String type)   { return el("input").attr("type", type); }
    public static VNode textarea(Object... c){ return el("textarea", c); }
    public static VNode form(Object... c)    { return el("form", c); }
    public static VNode label(Object... c)   { return el("label", c); }
    public static VNode select(Object... c)  { return el("select", c); }
    public static VNode option(Object... c)  { return el("option", c); }
    public static VNode ul(Object... c)      { return el("ul", c); }
    public static VNode ol(Object... c)      { return el("ol", c); }
    public static VNode li(Object... c)      { return el("li", c); }
    public static VNode p(Object... c)       { return el("p", c); }
    public static VNode h1(Object... c)      { return el("h1", c); }
    public static VNode h2(Object... c)      { return el("h2", c); }
    public static VNode h3(Object... c)      { return el("h3", c); }
    public static VNode section(Object... c) { return el("section", c); }
    public static VNode header(Object... c)  { return el("header", c); }
    public static VNode footer(Object... c)  { return el("footer", c); }
    public static VNode main(Object... c)    { return el("main", c); }
    public static VNode nav(Object... c)     { return el("nav", c); }
    public static VNode strong(Object... c)  { return el("strong", c); }
    public static VNode em(Object... c)      { return el("em", c); }
    public static VNode small(Object... c)   { return el("small", c); }
    public static VNode code(Object... c)    { return el("code", c); }
    public static VNode pre(Object... c)     { return el("pre", c); }
    public static VNode table(Object... c)   { return el("table", c); }
    public static VNode thead(Object... c)   { return el("thead", c); }
    public static VNode tbody(Object... c)   { return el("tbody", c); }
    public static VNode tr(Object... c)      { return el("tr", c); }
    public static VNode th(Object... c)      { return el("th", c); }
    public static VNode td(Object... c)      { return el("td", c); }
    public static VNode br()                 { return el("br"); }
    public static VNode hr()                 { return el("hr"); }

    /** An external or non-routed anchor. For in-app navigation use {@link #link}. */
    public static VNode a(String href, Object... c) { return el("a", c).attr("href", href); }

    /** {@code alt} is required, not optional — a screen reader has nothing else to read. */
    public static VNode img(String src, String alt) {
        return el("img").attr("src", src).attr("alt", alt);
    }

    /**
     * An anchor that routes in-page via {@link Router}. It keeps a real
     * {@code href}, so middle-click, ctrl-click and "copy link address" behave
     * exactly like any other link — only a plain left click is intercepted.
     */
    public static VNode link(String path, String label) {
        return el("a", label).attr("href", path).on("click", e -> {
            if (!isPlainLeftClick(e)) {
                return;                                  // let the browser open it its way
            }
            e.preventDefault();
            Router.navigate(path);
        });
    }

    // Reading the fields directly: casting Event to MouseEvent traps under WasmGC.
    @JSBody(params = "e", script = "return e.button === 0 && !e.ctrlKey && !e.metaKey "
            + "&& !e.shiftKey && !e.altKey;")
    private static native boolean isPlainLeftClick(Event e);

    @JSBody(params = "e", script = "return !!e.target.checked;")
    private static native boolean isChecked(Event e);
}
