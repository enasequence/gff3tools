package uk.ac.ebi.embl.gff3tools.validation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ValidationClass {
    String name() default "";
    String description() default "";
    boolean enabled() default true;
}