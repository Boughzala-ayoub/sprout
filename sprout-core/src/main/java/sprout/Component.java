package sprout;

/**
 * Base class for a component (React's {@code React.Component}). Subclass it,
 * hold {@link State} fields, and implement {@link #render()}.
 *
 * <p>Read {@link #props} for data passed by the parent. Pull services from the
 * application context with {@link #use(Class)} (the DI seam). Override
 * {@link #onMount()} / {@link #onUnmount()} for lifecycle work.
 */
public abstract class Component {
    /** Data passed down from the parent. Set by the reconciler before each render. */
    protected Props props = new Props();

    AppContext context;    // set by the reconciler
    Instance instance;     // set by the reconciler

    public abstract VNode render();

    protected void onMount() {
    }

    protected void onUnmount() {
    }

    /** Dependency injection: fetch a service from the application context. */
    protected <T> T use(Class<T> type) {
        return context.get(type);
    }
}
