package sprout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.teavm.jso.JSBody;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.xml.Node;

/**
 * The reconciler's "fiber": one persistent instance per rendered {@link VNode},
 * mirroring the tree so that component state and DOM nodes survive across
 * re-renders. This is the engine that makes composition + local re-render work.
 *
 * <p>Invariant: every instance resolves to exactly one representative DOM node
 * ({@link #domNode()}) that contains its whole subtree — a host element, a text
 * node, a fragment's {@code display:contents} wrapper, or (for a component) the
 * node of whatever it rendered. That single-node invariant is what keeps
 * insert/move/remove simple.
 */
final class Instance {
    int kind;
    VNode vnode;
    Node dom;                                  // HOST/TEXT/FRAGMENT node; null for COMPONENT
    HTMLElement parentDom;
    final List<Instance> children = new ArrayList<>();
    Component component;                        // COMPONENT only
    AppContext context;

    // Host event wiring: a stable dispatcher per event type reads the current
    // handler, so re-renders that swap the closure (new props) are picked up.
    Map<String, Consumer<Event>> handlers = Collections.emptyMap();
    final Set<String> bound = new HashSet<>();

    final Runnable rerender = this::rerenderComponent;   // stable identity for State subscriptions
    final Set<State<?>> deps = new HashSet<>();          // States read during the last render
    boolean unmounted;

    private static HTMLDocument doc() {
        return HTMLDocument.current();
    }

    Node domNode() {
        return kind == VNode.COMPONENT ? children.get(0).domNode() : dom;
    }

    // ---- mount ------------------------------------------------------------

    static Instance mount(VNode v, HTMLElement parent, AppContext ctx) {
        v = normalize(v);
        Instance inst = new Instance();
        inst.vnode = v;
        inst.parentDom = parent;
        inst.context = ctx;
        inst.kind = v.kind;

        switch (v.kind) {
            case VNode.TEXT: {
                inst.dom = doc().createTextNode(v.text);
                parent.appendChild(inst.dom);
                break;
            }
            case VNode.HOST: {
                HTMLElement el = doc().createElement(v.tag);
                inst.dom = el;
                inst.applyHost(el, null, v);
                parent.appendChild(el);
                for (VNode c : v.children) {
                    inst.children.add(mount(c, el, ctx));
                }
                break;
            }
            case VNode.FRAGMENT: {
                HTMLElement frag = doc().createElement("div");
                frag.setAttribute("style", "display:contents");
                inst.dom = frag;
                parent.appendChild(frag);
                for (VNode c : v.children) {
                    inst.children.add(mount(c, frag, ctx));
                }
                break;
            }
            case VNode.COMPONENT: {
                Component comp = v.factory.get();
                comp.props = v.props;
                comp.context = ctx;
                comp.instance = inst;
                Sprout.injector.inject(comp, ctx);       // set @Autowired fields before first render
                inst.component = comp;
                VNode out = renderComponent(inst);
                inst.children.add(mount(out, parent, ctx));  // component owns no wrapper node
                comp.onMount();
                break;
            }
            default:
                throw new IllegalStateException("unknown kind " + v.kind);
        }
        return inst;
    }

    private static Instance mountBefore(VNode v, HTMLElement parent, Node ref, AppContext ctx) {
        Instance i = mount(v, parent, ctx);
        if (ref != null && ref.getParentNode() == parent) {
            parent.insertBefore(i.domNode(), ref);
        }
        return i;
    }

    // ---- patch ------------------------------------------------------------

    static Instance patch(Instance inst, VNode newV) {
        newV = normalize(newV);
        if (!compatible(inst, newV)) {
            Instance replacement = mountBefore(newV, inst.parentDom, inst.domNode(), inst.context);
            unmount(inst);
            return replacement;
        }
        switch (inst.kind) {
            case VNode.TEXT:
                if (!Objects.equals(inst.vnode.text, newV.text)) {
                    inst.dom.setNodeValue(newV.text);
                }
                break;
            case VNode.HOST:
                inst.applyHost((HTMLElement) inst.dom, inst.vnode, newV);
                inst.reconcileChildren((HTMLElement) inst.dom, newV.children);
                break;
            case VNode.FRAGMENT:
                inst.reconcileChildren((HTMLElement) inst.dom, newV.children);
                break;
            case VNode.COMPONENT:
                inst.component.props = newV.props;
                inst.vnode = newV;
                inst.children.set(0, patch(inst.children.get(0), renderComponent(inst)));
                break;
            default:
                break;
        }
        inst.vnode = newV;
        return inst;
    }

    // ---- component render, with State dependency tracking -----------------

    private static VNode renderComponent(Instance inst) {
        Instance previous = Sprout.active;
        Sprout.active = inst;
        inst.dropSubscriptions();          // this render re-collects them, so stale deps go away
        try {
            return normalize(inst.component.render());
        } catch (RuntimeException e) {
            // Error boundary: show the failure in place, keep the rest of the page alive.
            Sprout.reportError(inst.component.getClass().getName() + ".render", e);
            return VNode.el("div", inst.component.getClass().getSimpleName() + " failed: " + e)
                    .cls("sprout-error");
        } finally {
            Sprout.active = previous;
        }
    }

    /** Called (via {@link State}) when this component's state changed. */
    void rerenderComponent() {
        if (kind != VNode.COMPONENT || unmounted) {
            return;                        // a queued re-render can outlive its component
        }
        children.set(0, patch(children.get(0), renderComponent(this)));
    }

    private void dropSubscriptions() {
        for (State<?> s : deps) {
            s.unsubscribe(rerender);
        }
        deps.clear();
    }

    // ---- keyed children reconciliation ------------------------------------

    // ponytail: walks the target order and only touches nodes already out of
    // place. Moving a node re-inserts it, which drops focus and selection, so
    // "move everything" is not an option even for short lists. Still not
    // move-minimal; swap in longest-increasing-subsequence if a list gets big.
    private void reconcileChildren(HTMLElement parent, List<VNode> newVnodes) {
        Map<String, Instance> oldByKey = new LinkedHashMap<>();
        for (int i = 0; i < children.size(); i++) {
            oldByKey.put(keyOf(children.get(i).vnode, i), children.get(i));
        }

        List<Instance> result = new ArrayList<>();
        Set<Instance> reused = new HashSet<>();
        for (int i = 0; i < newVnodes.size(); i++) {
            VNode v = normalize(newVnodes.get(i));
            Instance old = oldByKey.get(keyOf(v, i));
            if (old != null && compatible(old, v)) {
                result.add(patch(old, v));
                reused.add(old);
            } else {
                result.add(mount(v, parent, context));
            }
        }
        for (Instance old : children) {
            if (!reused.contains(old)) {
                unmount(old);
            }
        }
        Node expected = parent.getFirstChild();
        for (Instance inst : result) {
            Node d = inst.domNode();
            if (d == expected) {
                expected = expected.getNextSibling();      // already in place — leave the DOM alone
            } else {
                parent.insertBefore(d, expected);          // null ref == append
            }
        }
        children.clear();
        children.addAll(result);
    }

    private static String keyOf(VNode v, int index) {
        return v.key != null ? "k:" + v.key : "i:" + index;   // unkeyed falls back to position
    }

    // ---- host attributes + events -----------------------------------------

    private void applyHost(HTMLElement el, VNode oldV, VNode newV) {
        if (oldV != null) {
            for (String name : oldV.attrs.keySet()) {
                if (!newV.attrs.containsKey(name)) {
                    if (name.equals("value")) {
                        setValueProperty(el, "");
                    } else if (name.equals("checked")) {
                        setCheckedProperty(el, false);
                    } else {
                        el.removeAttribute(name);
                    }
                }
            }
        }
        for (Map.Entry<String, String> a : newV.attrs.entrySet()) {
            String name = a.getKey();
            String value = a.getValue();
            // `value` and `checked` are properties, not attributes, and the user
            // edits them behind the vnode's back. Comparing against the previous
            // vnode would then skip the write on a re-render that "changed
            // nothing" and leave the drift on screen — the classic controlled-
            // input bug. Compare against the live element instead (in the JS
            // helper), so a rejected edit really does snap back.
            if (name.equals("value")) {
                setValueProperty(el, value == null ? "" : value);
            } else if (name.equals("checked")) {
                setCheckedProperty(el, true);
            } else if (!Objects.equals(oldV == null ? null : oldV.attrs.get(name), value)) {
                el.setAttribute(name, value);
            }
        }
        handlers = newV.handlers;
        for (String event : newV.handlers.keySet()) {
            if (bound.add(event)) {
                el.addEventListener(event, (EventListener<Event>) evt -> {
                    Consumer<Event> h = handlers.get(event);
                    if (h != null) {
                        try {
                            h.accept(evt);
                        } catch (RuntimeException e) {
                            Sprout.reportError("on" + event + " handler", e);   // a bad click ≠ a dead UI
                        }
                    }
                });
            }
        }
    }

    // ---- unmount ----------------------------------------------------------

    static void unmount(Instance inst) {
        fireUnmount(inst);
        Node d = inst.domNode();
        Node parent = d.getParentNode();
        if (parent != null) {
            parent.removeChild(d);                         // removes the whole subtree in one op
        }
    }

    private static void fireUnmount(Instance inst) {
        for (Instance c : inst.children) {
            fireUnmount(c);
        }
        inst.unmounted = true;
        inst.dropSubscriptions();          // stop every State from holding this component alive
        if (inst.component != null) {
            try {
                inst.component.onUnmount();
            } catch (RuntimeException e) {
                Sprout.reportError(inst.component.getClass().getName() + ".onUnmount", e);
            }
        }
    }

    // ---- helpers ----------------------------------------------------------

    private static boolean compatible(Instance inst, VNode v) {
        if (inst.kind != v.kind) {
            return false;
        }
        switch (v.kind) {
            case VNode.HOST:      return Objects.equals(inst.vnode.tag, v.tag);
            case VNode.COMPONENT: return inst.component.getClass() == v.type;
            default:              return true;             // TEXT, FRAGMENT
        }
    }

    private static VNode normalize(VNode v) {
        return v == null ? VNode.text("") : v;             // null render => invisible placeholder
    }

    // Written as properties, and only when they actually differ — a redundant
    // `el.value = x` moves the caret to the end of the field mid-typing.
    // @JSBody rather than a cast to HTMLInputElement: JSO casts trap under WasmGC.

    @JSBody(params = {"el", "value"}, script = "if (el.value !== value) el.value = value;")
    private static native void setValueProperty(HTMLElement el, String value);

    @JSBody(params = {"el", "on"}, script = "if (el.checked !== on) el.checked = on;")
    private static native void setCheckedProperty(HTMLElement el, boolean on);
}
