package sprout;

import java.util.HashMap;
import java.util.Map;

/**
 * A small bag of data passed from a parent component to a child (React's
 * {@code props}). Chainable: {@code new Props().set("text", t).set("onRemove", r)}.
 */
public class Props {
    private final Map<String, Object> map = new HashMap<>();

    public Props set(String name, Object value) {
        map.put(name, value);
        return this;
    }

    public Object get(String name) {
        return map.get(name);
    }

    public String str(String name) {
        Object v = map.get(name);
        return v == null ? null : v.toString();
    }

    public int getInt(String name) {
        Object v = map.get(name);
        return v == null ? 0 : ((Number) v).intValue();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String name, Class<T> type) {
        return (T) map.get(name);
    }

    /** Read a callback prop. */
    public Runnable runnable(String name) {
        return (Runnable) map.get(name);
    }
}
