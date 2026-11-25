package sprout;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to be injected from the {@link AppContext} — Spring's
 * {@code @Autowired}. Wiring is generated at build time by the annotation
 * processor (see {@code sprout.generated.SproutBoot}).
 *
 * <p>The field must be public: the generated wiring sets it directly (no
 * reflection) from another package.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Autowired {
}
