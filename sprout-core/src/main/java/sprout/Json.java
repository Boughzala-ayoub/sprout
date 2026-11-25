package sprout;

import java.util.ArrayList;
import java.util.List;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.json.JSON;

/**
 * JSON in both directions, backed by the browser's own {@code JSON.parse} and
 * {@code JSON.stringify} — no parser to ship, no reflection (which TeaVM cannot
 * do anyway), so mapping is explicit. Reading:
 *
 * <pre>
 * Json body = response.json();
 * String name = body.get("user").get("name").asString();
 * for (Json todo : body.get("todos").items()) {
 *     list.add(new Todo(todo.get("id").asInt(), todo.get("text").asString()));
 * }
 * </pre>
 *
 * <p>Every accessor is null-safe: reading a missing key gives an empty node, and
 * {@code asString()} on it returns {@code ""}. Malformed input parses to an
 * empty node rather than throwing, so a bad response degrades to empty UI
 * instead of a blank page — check {@link #isMissing()} when you need to tell
 * "absent" from "empty".
 *
 * <p>Values are read through {@code @JSBody} helpers rather than casts to
 * {@code JSString}/{@code JSNumber}: casting between JSO types traps under
 * WasmGC.
 */
public final class Json {

    private static final Json MISSING = new Json(null);

    private final JSObject value;

    private Json(JSObject value) {
        this.value = value;
    }

    public static Json parse(String text) {
        if (text == null || text.isEmpty()) {
            return MISSING;
        }
        try {
            JSObject parsed = JSON.parse(text);
            return parsed == null ? MISSING : new Json(parsed);
        } catch (RuntimeException e) {
            Sprout.reportError("Json.parse", e);
            return MISSING;
        }
    }

    /** Serializes back to JSON text. */
    public static String stringify(Json json) {
        return json.value == null ? "null" : JSON.stringify(json.value);
    }

    /** A named property, or an empty node when absent. */
    public Json get(String key) {
        if (value == null) {
            return MISSING;
        }
        JSObject child = property(value, key);
        return child == null ? MISSING : new Json(child);
    }

    /** An array element, or an empty node when out of range. */
    public Json at(int index) {
        if (value == null || index < 0 || index >= size()) {
            return MISSING;
        }
        JSObject child = element(value, index);
        return child == null ? MISSING : new Json(child);
    }

    /** The elements of an array; an empty list for anything else. */
    public List<Json> items() {
        List<Json> out = new ArrayList<>();
        for (int i = 0, n = size(); i < n; i++) {
            out.add(at(i));
        }
        return out;
    }

    /** Element count for an array, 0 otherwise. */
    public int size() {
        return value == null ? 0 : length(value);
    }

    public boolean isArray() {
        return value != null && isJsArray(value);
    }

    /** True when this key was absent, null, or the document failed to parse. */
    public boolean isMissing() {
        return value == null;
    }

    public String asString() {
        return value == null ? "" : asText(value);
    }

    public double asDouble() {
        return value == null ? 0 : asNumber(value);
    }

    public int asInt() {
        return (int) asDouble();
    }

    public boolean asBoolean() {
        return value != null && isTrue(value);
    }

    @Override
    public String toString() {
        return stringify(this);
    }

    // ---- writing ----------------------------------------------------------

    /**
     * Starts a JSON object to send. Values go into a real JS object and the
     * browser's own {@code JSON.stringify} produces the text, so quoting,
     * escaping, newlines and unicode are correct without a line of code here:
     * <pre>
     * Http.post("/api/todos")
     *     .json(Json.obj().put("text", text).put("done", false))
     *     .send(response -&gt; ...);
     * </pre>
     */
    public static Builder obj() {
        return new Builder(newObject());
    }

    /** Starts a JSON array; fill it with {@link Builder#add}. */
    public static Builder arr() {
        return new Builder(newArray());
    }

    /** Builds JSON to send. Objects use {@link #put}, arrays use {@link #add}. */
    public static final class Builder {

        // ponytail: one type for both shapes, so put() on an array is nonsense
        // the compiler cannot catch. Split it if that ever actually bites.
        private final JSObject target;
        private int length;                     // next array index

        private Builder(JSObject target) {
            this.target = target;
        }

        public Builder put(String key, String value) {
            if (value == null) {
                putNull(target, key);           // not putString: a null String traps crossing into JS
            } else {
                putString(target, key, value);
            }
            return this;
        }

        public Builder put(String key, double value)  { putDouble(target, key, value); return this; }
        public Builder put(String key, boolean value) { putBool(target, key, value); return this; }
        public Builder put(String key, Builder value) { putObject(target, key, value.target); return this; }

        // Assigning a[«index»] appends and updates length — JS arrays coerce
        // numeric string keys — so add() is put() and needs no code of its own.
        public Builder add(String value)  { return put(String.valueOf(length++), value); }
        public Builder add(double value)  { return put(String.valueOf(length++), value); }
        public Builder add(boolean value) { return put(String.valueOf(length++), value); }
        public Builder add(Builder value) { return put(String.valueOf(length++), value); }

        /** The JSON text, ready to send. */
        @Override
        public String toString() {
            return JSON.stringify(target);
        }
    }

    // ---- JS accessors (no casts: JSO casts trap under WasmGC) --------------

    @JSBody(script = "return {};")
    private static native JSObject newObject();

    @JSBody(script = "return [];")
    private static native JSObject newArray();

    @JSBody(params = {"o", "k", "v"}, script = "o[k] = v;")
    private static native void putString(JSObject o, String k, String v);

    @JSBody(params = {"o", "k", "v"}, script = "o[k] = v;")
    private static native void putDouble(JSObject o, String k, double v);

    @JSBody(params = {"o", "k", "v"}, script = "o[k] = v;")
    private static native void putBool(JSObject o, String k, boolean v);

    @JSBody(params = {"o", "k", "v"}, script = "o[k] = v;")
    private static native void putObject(JSObject o, String k, JSObject v);

    @JSBody(params = {"o", "k"}, script = "o[k] = null;")
    private static native void putNull(JSObject o, String k);

    @JSBody(params = {"o", "key"}, script = "var v = o[key]; return v === undefined ? null : v;")
    private static native JSObject property(JSObject o, String key);

    @JSBody(params = {"o", "i"}, script = "var v = o[i]; return v === undefined ? null : v;")
    private static native JSObject element(JSObject o, int i);

    @JSBody(params = "o", script = "return Array.isArray(o) ? o.length : 0;")
    private static native int length(JSObject o);

    @JSBody(params = "o", script = "return Array.isArray(o);")
    private static native boolean isJsArray(JSObject o);

    @JSBody(params = "o", script = "return typeof o === 'object' ? JSON.stringify(o) : String(o);")
    private static native String asText(JSObject o);

    @JSBody(params = "o", script = "return typeof o === 'number' ? o : 0;")
    private static native double asNumber(JSObject o);

    @JSBody(params = "o", script = "return o === true;")
    private static native boolean isTrue(JSObject o);
}
