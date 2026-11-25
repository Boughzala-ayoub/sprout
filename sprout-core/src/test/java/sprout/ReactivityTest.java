package sprout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.jso.JSBody;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTestRunner;

/**
 * The parts that only a real DOM can prove: subscriptions, batching, keyed
 * reconciliation, and the error boundary. Compiled to WasmGC and run in a
 * headless browser by {@link TeaVMTestRunner} — the same target the app ships on.
 */
@RunWith(TeaVMTestRunner.class)
@SkipJVM                                   // there is no DOM on the JVM
public class ReactivityTest {

    private static HTMLElement freshMount() {
        HTMLElement host = HTMLDocument.current().createElement("div");
        HTMLDocument.current().getBody().appendChild(host);
        return host;
    }

    /** A component that counts its own renders and shows a State's value. */
    static final class Probe extends Component {
        static int renders;
        static State<String> source;

        static VNode of(Props props) {
            return VNode.component(Probe.class, Probe::new, props);
        }

        @Override
        public VNode render() {
            renders++;
            return VNode.p(source.get());
        }
    }

    @Test
    public void readingStateDuringRenderSubscribes() {
        Probe.source = new State<>("first");
        Probe.renders = 0;
        Instance.mount(Probe.of(new Props()), freshMount(), new AppContext());

        assertEquals(1, Probe.renders);
        assertEquals(1, Probe.source.subscriberCount());
    }

    @Test
    public void unmountDropsSubscriptions() {
        // The leak that matters: a State owned by an @Service outlives the page
        // that read it, and would otherwise re-render it forever.
        State<String> shared = new State<>("v");
        Probe.source = shared;
        HTMLElement host = freshMount();
        Instance instance = Instance.mount(Probe.of(new Props()), host, new AppContext());
        assertEquals(1, shared.subscriberCount());

        Instance.unmount(instance);

        assertEquals(0, shared.subscriberCount());
        int before = Probe.renders;
        shared.set("changed");
        Sprout.flush();
        assertEquals("unmounted component must not re-render", before, Probe.renders);
    }

    @Test
    public void writesInOneTaskRenderOnce() {
        Probe.source = new State<>("a");
        State<String> other = new State<>("x");
        Probe.renders = 0;
        Instance.mount(Probe.of(new Props()), freshMount(), new AppContext());
        assertEquals(1, Probe.renders);

        Probe.source.set("b");
        Probe.source.set("c");
        other.set("y");
        assertEquals("renders are queued, not immediate", 1, Probe.renders);

        Sprout.flush();
        assertEquals("three writes, one repaint", 2, Probe.renders);
    }

    @Test
    public void unchangedValueDoesNotRender() {
        Probe.source = new State<>("same");
        Probe.renders = 0;
        Instance.mount(Probe.of(new Props()), freshMount(), new AppContext());

        Probe.source.set("same");
        Sprout.flush();

        assertEquals(1, Probe.renders);
    }

    /** Renders a collection, so an in-place change has something to show. */
    static final class ListProbe extends Component {
        static State<List<String>> source;

        static VNode of(Props props) {
            return VNode.component(ListProbe.class, ListProbe::new, props);
        }

        @Override
        public VNode render() {
            return VNode.p(source.get().toString());
        }
    }

    @Test
    public void mutateRendersWhereSetIsASilentNoOp() {
        List<String> initial = new ArrayList<>();
        initial.add("a");
        ListProbe.source = new State<>(initial);
        HTMLElement host = freshMount();
        Instance.mount(ListProbe.of(new Props()), host, new AppContext());
        assertEquals("[a]", ((HTMLElement) host.getFirstChild()).getInnerHTML());

        // The trap mutate() exists for: change the list, hand it back to set(),
        // and set() finds it equal to itself and does nothing whatsoever.
        ListProbe.source.get().add("b");
        ListProbe.source.set(ListProbe.source.get());
        Sprout.flush();
        assertEquals("set() on an equal list changes nothing",
                "[a]", ((HTMLElement) host.getFirstChild()).getInnerHTML());

        ListProbe.source.mutate(list -> list.add("c"));
        Sprout.flush();
        assertEquals("[a, b, c]", ((HTMLElement) host.getFirstChild()).getInnerHTML());
    }

    @Test
    public void checkedIsWrittenAsAPropertyOnEveryPatch() {
        HTMLElement host = freshMount();
        Instance box = Instance.mount(VNode.input("checkbox").attr("checked", true),
                host, new AppContext());
        HTMLElement el = (HTMLElement) box.dom;
        assertTrue(isChecked(el));

        // The user unticks it. The vnode is unchanged, so diffing the vnodes
        // would skip the write and leave the box disagreeing with the State.
        el.click();
        assertFalse(isChecked(el));

        Instance.patch(box, VNode.input("checkbox").attr("checked", true));
        assertTrue("a re-render must snap the box back to what state says", isChecked(el));
    }

    @JSBody(params = "el", script = "return !!el.checked;")
    private static native boolean isChecked(HTMLElement el);

    // ---- keyed reconciliation ---------------------------------------------

    static VNode item(String key, String text) {
        return VNode.li(text).key(key);
    }

    @Test
    public void keyedChildrenKeepTheirDomNodesWhenReordered() {
        HTMLElement host = freshMount();
        Instance list = Instance.mount(
                VNode.ul(item("a", "A"), item("b", "B"), item("c", "C")), host, new AppContext());
        Object nodeB = list.children.get(1).dom;

        Instance.patch(list, VNode.ul(item("c", "C"), item("b", "B"), item("a", "A")));

        assertSame("same key must keep the same DOM node", nodeB, list.children.get(1).dom);
        assertEquals("C", ((HTMLElement) list.children.get(0).dom).getInnerHTML());
        assertEquals("A", ((HTMLElement) list.children.get(2).dom).getInnerHTML());
        assertEquals(3, list.children.size());
    }

    @Test
    public void reconcilerDoesNotTouchNodesAlreadyInPlace() {
        // Re-inserting a node drops focus and selection, so unchanged children
        // must be left alone entirely.
        HTMLElement host = freshMount();
        HTMLElement input = HTMLDocument.current().createElement("input");
        Instance form = Instance.mount(VNode.div(VNode.el("input"), VNode.button("Add")),
                host, new AppContext());
        input = (HTMLElement) form.children.get(0).dom;
        input.focus();
        assertSame(input, HTMLDocument.current().getActiveElement());

        Instance.patch(form, VNode.div(VNode.el("input"), VNode.button("Add")));

        assertSame("focus must survive a re-render", input, HTMLDocument.current().getActiveElement());
    }

    @Test
    public void removedChildrenLeaveTheDom() {
        HTMLElement host = freshMount();
        Instance list = Instance.mount(VNode.ul(item("a", "A"), item("b", "B")), host, new AppContext());

        Instance.patch(list, VNode.ul(item("b", "B")));

        assertEquals(1, list.children.size());
        assertEquals(1, ((HTMLElement) host.getFirstChild()).getChildNodes().getLength());
    }

    // ---- error boundary ----------------------------------------------------

    static final class Exploding extends Component {
        static VNode of(Props props) {
            return VNode.component(Exploding.class, Exploding::new, props);
        }

        @Override
        public VNode render() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    public void aComponentThatThrowsDoesNotKillThePage() {
        HTMLElement host = freshMount();

        Instance app = Instance.mount(
                VNode.div(VNode.p("before"), Exploding.of(new Props()), VNode.p("after")),
                host, new AppContext());

        assertNotNull(app);
        HTMLElement root = (HTMLElement) host.getFirstChild();
        assertEquals("siblings still render", 3, root.getChildNodes().getLength());
        String html = root.getInnerHTML();
        assertTrue("the failure is shown in place: " + html, html.contains("sprout-error"));
        assertTrue(html.contains("before"));
        assertTrue(html.contains("after"));
    }

    @Test
    public void aHandlerThatThrowsDoesNotKillThePage() {
        HTMLElement host = freshMount();
        Instance.mount(VNode.button("boom").onClick(() -> {
            throw new IllegalStateException("handler blew up");
        }), host, new AppContext());

        ((HTMLElement) host.getFirstChild()).click();      // must not propagate out of the dispatcher

        assertFalse(HTMLDocument.current().getBody().getInnerHTML().isEmpty());
    }
}
