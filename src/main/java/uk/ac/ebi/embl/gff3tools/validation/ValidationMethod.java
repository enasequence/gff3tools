package uk.ac.ebi.embl.gff3tools.validation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ValidationMethod {
    String rule() default "";
    ValidationType type();
    RuleSeverity severity() default RuleSeverity.ERROR;
    String description() default "";
}
