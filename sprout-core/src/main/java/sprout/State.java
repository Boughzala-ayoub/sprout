package sprout;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * A reactive value. Reading it during a component's render subscribes that
 * component; writing a new value re-renders every subscriber. The engine of
 * reactivity — same idea as React state / Solid signals.
 */
public class State<T> {
    private T value;

    /**
     * Exactly the components that read this during their last render. Each one
     * drops its subscriptions before re-rendering and when it unmounts, so a
     * State that outlives a component — one held by an {@code @Service}
     * singleton, say — does not keep re-rendering the dead.
     */
    private final Set<Runnable> subscribers = new LinkedHashSet<>();

    public State(T initial) {
        this.value = initial;
    }

    public T get() {
        Instance reader = Sprout.active;
        if (reader != null) {
            subscribers.add(reader.rerender);
            reader.deps.add(this);              // so the reader can unsubscribe later
        }
        return value;
    }

    public void set(T next) {
        if (Objects.equals(value, next)) {
            return;
        }
        value = next;
        notifySubscribers();
    }

    /**
     * Changes the value in place, then re-renders. {@link #set} compares with
     * {@code equals} and bails when nothing changed, which quietly makes
     * "mutate the list, set it back" do nothing at all — so a collection is
     * either copied on every edit, or updated here:
     * <pre>todos.mutate(list -&gt; list.add(todo));</pre>
     */
    public void mutate(Consumer<T> change) {
        change.accept(value);
        notifySubscribers();                    // no equals check: the old value *is* the new one
    }

    private void notifySubscribers() {
        for (Runnable effect : subscribers) {
            Sprout.schedule(effect);            // batched: repaint once at the end of the task
        }
    }

    void unsubscribe(Runnable effect) {
        subscribers.remove(effect);
    }

    int subscriberCount() {                     // for tests
        return subscribers.size();
    }

    /** Convenience for {@code set(f(get()))}. */
    public void update(UnaryOperator<T> f) {
        set(f.apply(value));
    }
}
