package sprout;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Spring-flavored marker for an injectable service. Documentation-only for now
 * (see {@link AppContext}); register the instance explicitly with
 * {@code context.provide(...)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Service {
}
