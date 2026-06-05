package dk.cintix.application.server.infrastructure.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author cix
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ApiDoc {
    String summary() default "";
    String description() default "";
    String tag() default "";
    boolean deprecated() default false;
    String requestBody() default "";
    String response200() default "";
    String response400() default "";
    String response401() default "";
    String example() default "";
    String responseExample() default "";
    String contentType() default "";
}
