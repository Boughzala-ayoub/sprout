package sprout;

import java.util.HashMap;
import java.util.Map;

/**
 * A minimal dependency-injection container — Spring's {@code ApplicationContext},
 * shrunk to its essence. Register services up front; components pull them with
 * {@link Component#use(Class)}.
 *
 * <p>ponytail: registration is explicit rather than classpath-scanned. Real
 * {@code @Service} scanning + {@code @Autowired} field injection needs runtime
 * reflection, which TeaVM only offers behind extra configuration. Explicit
 * wiring teaches the same DI concept without that machinery.
 */
public class AppContext {
    private final Map<Class<?>, Object> beans = new HashMap<>();

    public <T> AppContext provide(Class<T> type, T instance) {
        beans.put(type, instance);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        Object bean = beans.get(type);
        if (bean == null) {
            throw new IllegalStateException("No bean registered for " + type.getName());
        }
        return (T) bean;
    }
}
