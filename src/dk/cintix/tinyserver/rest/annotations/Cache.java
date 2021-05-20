/*
 */
package dk.cintix.tinyserver.rest.annotations;

import dk.cintix.tinyserver.rest.http.Status;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author cix
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(value = CacheByStatus.class)
public @interface Cache {
    long timeToLive() default -1;
    int size() default 10000;
    Status[] status() default {Status.All};
}