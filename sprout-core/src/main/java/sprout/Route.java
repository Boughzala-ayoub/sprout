package sprout;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Spring-flavored marker documenting the path a page component maps to. The
 * live route table is passed to {@link Router}; this annotation is a readable
 * signpost on the component itself.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Route {
    String value();
}
