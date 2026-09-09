/*
 * Copyright 2025 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.embl.gff3tools.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a tunable parameter on a {@code @ValidationMethod}/{@code @FixMethod}-annotated
 * method. The namespaced key exposed via {@code --params}/{@code ResolvedParameters} is derived
 * as {@code rule() + "." + name()} at scan time, using the owning method's own {@code rule()}, so
 * this annotation never restates it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Parameters.class)
public @interface Parameter {
    String name();

    ParameterType type();

    String description() default "";

    boolean mandatory() default false;

    /** Ignored if {@code mandatory=true}; otherwise coerced by {@link #type()}. */
    String defaultValue() default "";
}
