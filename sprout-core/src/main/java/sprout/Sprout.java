package sprout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * Framework entry point. Inspired by Spring's
 * {@code SpringApplication.run(App.class, args)}: hand it your root component, a
 * mount point, and an application context, and it takes over.
 */
public final class Sprout {

    /**
     * The component currently rendering. {@link State#get()} reads this to know
     * whom to subscribe, and records itself as one of that component's
     * dependencies so the subscription can be dropped again. Single-threaded
     * (JS), so one static field is enough.
     */
    static Instance active;

    // ---- batched re-rendering ---------------------------------------------

    private static final Set<Runnable> pending = new LinkedHashSet<>();
    private static boolean flushScheduled;

    /**
     * Queues a re-render for the end of the current task. Two {@code State.set}
     * calls in one event handler therefore repaint once, not twice, and a
     * component reading several changed States re-renders once (the set
     * de-duplicates).
     */
    static void schedule(Runnable effect) {
        pending.add(effect);
        if (!flushScheduled) {
            flushScheduled = true;
            Window.setTimeout(Sprout::flush, 0);
        }
    }

    static void flush() {
        flushScheduled = false;
        List<Runnable> batch = new ArrayList<>(pending);   // copy: a render may queue more
        pending.clear();
        for (Runnable effect : batch) {
            effect.run();
        }
    }

    /**
     * Where a component's render or event handler blew up. Logged, never
     * rethrown: one broken component must not take the whole page with it.
     */
    static void reportError(String where, Throwable e) {
        System.err.println("[sprout] " + where + " threw " + e);
        e.printStackTrace();
    }

    /** Sets {@code @Autowired} fields on a bean. Installed by generated {@code SproutBoot}. */
    public interface Injector {
        void inject(Object bean, AppContext context);
    }

    static Injector injector = (bean, context) -> { };   // no-op until DI is booted

    public static void setInjector(Injector i) {
        injector = i;
    }

    private Sprout() {
    }

    public static void run(VNode root, String mountId) {
        run(root, mountId, new AppContext());
    }

    public static void run(VNode root, String mountId, AppContext context) {
        HTMLElement mount = HTMLDocument.current().getElementById(mountId);
        Instance.mount(root, mount, context);
    }
}
