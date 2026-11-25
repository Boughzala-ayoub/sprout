package sprout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.util.Arrays;

import org.junit.Test;

/** Building the virtual tree touches no DOM, so it runs on the JVM. */
public class VNodeTest {

    @Test
    public void stringChildrenBecomeTextNodes() {
        VNode node = VNode.el("p", "hello");
        assertEquals(1, node.children.size());
        assertEquals(VNode.TEXT, node.children.get(0).kind);
        assertEquals("hello", node.children.get(0).text);
    }

    @Test
    public void listChildrenAreFlattened() {
        VNode list = VNode.ul(Arrays.asList(VNode.li("a"), VNode.li("b")), VNode.li("c"));
        assertEquals(3, list.children.size());
    }

    @Test
    public void nullAndFalseChildrenAreSkipped() {
        // `cond ? node : null` is the idiom for conditional rendering — it must not blow up.
        VNode node = VNode.div(VNode.p("kept"), null, false);
        assertEquals(1, node.children.size());
    }

    @Test
    public void attributesAndKeyAreChainable() {
        VNode node = VNode.div().cls("row").id("main").key("k1").attr("data-x", "1");
        assertEquals("row", node.attrs.get("class"));
        assertEquals("main", node.attrs.get("id"));
        assertEquals("1", node.attrs.get("data-x"));
        assertEquals("k1", node.key);
    }

    @Test
    public void classesAccumulateAndSkipNulls() {
        // So a conditional class is `.cls("text", done ? "done" : null)` and not
        // string concatenation at every call site.
        assertEquals("todo text done", VNode.div().cls("todo").cls("text", null, "", "done")
                .attrs.get("class"));
    }

    @Test
    public void presenceAttributeIsAddedAndRemoved() {
        // .attr("disabled", "false") would leave the button disabled — HTML reads
        // any value at all as true — so the boolean overload removes the key.
        assertEquals("", VNode.button("Add").attr("disabled", true).attrs.get("disabled"));
        assertFalse(VNode.button("Add").attr("disabled", true).attr("disabled", false)
                .attrs.containsKey("disabled"));
    }

    @Test
    public void linkKeepsARealHref() {
        // Not "#/path": the router uses the History API, and the href must stay
        // usable for ctrl-click and "copy link address".
        assertEquals("/about", VNode.link("/about", "About").attrs.get("href"));
    }

    @Test
    public void nullTextIsEmptyNotNull() {
        assertEquals("", VNode.text(null).text);
    }

    @Test
    public void propsCarryValuesUnchanged() {
        Runnable callback = () -> { };
        Props props = new Props().set("text", "buy milk").set("onRemove", callback);
        assertEquals("buy milk", props.get("text"));
        assertSame(callback, props.runnable("onRemove"));
    }
}
