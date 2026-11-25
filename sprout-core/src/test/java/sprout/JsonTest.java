package sprout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTestRunner;

/**
 * Json is the browser's own JSON.parse/stringify behind a Java API, so there is
 * nothing to test off the browser.
 */
@RunWith(TeaVMTestRunner.class)
@SkipJVM
public class JsonTest {

    @Test
    public void builtJsonRoundTrips() {
        String text = Json.obj()
                .put("text", "say \"hi\"\n")          // the escaping we deliberately did not write
                .put("done", false)
                .put("count", 2)
                .put("tags", Json.arr().add("a").add("b"))
                .toString();

        Json back = Json.parse(text);
        assertEquals("say \"hi\"\n", back.get("text").asString());
        assertFalse(back.get("done").asBoolean());
        assertEquals(2, back.get("count").asInt());
        assertTrue("add() must produce an array, not an object", back.get("tags").isArray());
        assertEquals(2, back.get("tags").size());
        assertEquals("b", back.get("tags").at(1).asString());
    }

    @Test
    public void nullValueDoesNotCrossIntoJs() {
        // Passing a null String into JS traps under WasmGC, uncatchably — so a
        // null value has to be written as a JS null instead of handed over.
        Json back = Json.parse(Json.obj().put("text", (String) null).put("keep", "x").toString());

        assertTrue(back.get("text").isMissing());
        assertEquals("x", back.get("keep").asString());
    }

    @Test
    public void parsedFailureIsEmptyNotAnException() {
        assertTrue(Json.parse("{not json").isMissing());
    }
}
