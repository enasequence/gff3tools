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

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.gff3tools.exception.AggregatedValidationException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.metrics.MetricsCollector;
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

public class ValidationEngine implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ValidationEngine.class);

    private final List<ValidationException> parsingWarnings;
    private final List<ValidationException> collectedErrors;
    private final boolean failFast;

    private final ValidationConfig validationConfig;
    private final ValidationRegistry validationRegistry;
    private final ValidationContext context;

    // Nullable: metrics collection is opt-in via --metrics (or the builder API).
    private MetricsCollector metrics;

    ValidationEngine(
            ValidationConfig validationConfig,
            ValidationRegistry validationRegistry,
            ValidationContext context,
            boolean failFast) {
        this.parsingWarnings = new java.util.ArrayList<>();
        this.collectedErrors = new java.util.ArrayList<>();
        this.failFast = failFast;
        this.validationConfig = validationConfig;
        this.validationRegistry = validationRegistry;
        this.context = context;
    }

    public ValidationContext getContext() {
        return context;
    }

    /** Attaches an optional {@link MetricsCollector}; without one, no metrics are collected. */
    public void setMetrics(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    /**
     * Executes fixes and validations interleaved by priority tier.
     * For each tier (CRITICAL → HIGH → NORMAL → LOW), fixes run first, then validations.
     * In fail-fast mode, an error at a given tier prevents lower-priority tiers from executing.
     */
    public <T> void validate(T target, int line) throws ValidationException {
        Map<ValidationPriority, List<ValidatorDescriptor>> fixesByPriority = validationRegistry.getFixesByPriority();
        Map<ValidationPriority, List<ValidatorDescriptor>> validationsByPriority =
                validationRegistry.getValidationsByPriority();

        try {
            for (ValidationPriority priority : ValidationPriority.values()) {
                executeFixes(target, line, fixesByPriority.getOrDefault(priority, List.of()));
                executeValidations(target, line, validationsByPriority.getOrDefault(priority, List.of()));
            }
        } finally {
            // Record after the tiers ran so features added by fixes (e.g. GAP_GENERATION) are
            // counted too, and in a finally so an aborted run still reports what it saw. The
            // version header feeds through as well, so the report carries the file's GFF3 spec.
            if (metrics != null && target instanceof GFF3Annotation annotation) {
                metrics.record(annotation);
            } else if (metrics != null && target instanceof GFF3Header header) {
                metrics.recordGff3Spec(header.version());
            }
        }
    }

    public <T> void executeValidations(T target, int line) throws ValidationException {
        executeValidations(target, line, validationRegistry.getValidations());
    }

    private <T> void executeValidations(T target, int line, List<ValidatorDescriptor> validators)
            throws ValidationException {

        for (ValidatorDescriptor validator : validators) {

            ValidationMethod methodAnnotation = validator.method().getAnnotation(ValidationMethod.class);
            RuleSeverity ruleSeverity =
                    validationConfig.getSeverity(methodAnnotation.rule(), methodAnnotation.severity());

            if (ruleSeverity == RuleSeverity.OFF) continue;

            try {
                if (target instanceof GFF3Feature && methodAnnotation.type() == ValidationType.FEATURE) {
                    validator.method().invoke(validator.instance(), target, line);
                } else if (target instanceof GFF3Annotation && methodAnnotation.type() == ValidationType.ANNOTATION) {
                    validator.method().invoke(validator.instance(), target, line);
                }
            } catch (Exception e) {
                handleRuleException(e, ruleSeverity, methodAnnotation.rule());
            }
        }
    }

    public void executeExits() {
        List<ValidatorDescriptor> validators = validationRegistry.getExits();

        for (ValidatorDescriptor validator : validators) {
            try {
                validator.method().invoke(validator.instance());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public <T> void executeFixes(T target, int line) throws ValidationException {
        executeFixes(target, line, validationRegistry.getFixs());
    }

    private <T> void executeFixes(T target, int line, List<ValidatorDescriptor> validators) throws ValidationException {

        for (ValidatorDescriptor validator : validators) {

            FixMethod methodAnnotation = validator.method().getAnnotation(FixMethod.class);

            boolean fixEnabled = validationConfig.getFix(methodAnnotation.rule(), methodAnnotation.enabled());

            if (!fixEnabled) continue;

            try {
                if (target instanceof GFF3Feature && methodAnnotation.type() == ValidationType.FEATURE) {
                    validator.method().invoke(validator.instance(), target, line);
                } else if (target instanceof GFF3Annotation && methodAnnotation.type() == ValidationType.ANNOTATION) {
                    validator.method().invoke(validator.instance(), target, line);
                }
            } catch (Exception e) {
                handleRuleException(e, null, methodAnnotation.rule());
            }
        }
    }

    public void handleSyntacticError(ValidationException exception) throws ValidationException {
        String rule = exception.getValidationRule() != null
                ? exception.getValidationRule().toString()
                : "SYNTAX_ERROR";
        RuleSeverity severity = validationConfig.getSeverity(rule, RuleSeverity.ERROR);
        switch (severity) {
            case OFF -> {}
            case WARN -> {
                parsingWarnings.add(exception);
                LOG.warn(exception.getMessage());
            }
            case ERROR -> {
                if (failFast) {
                    throw exception;
                } else {
                    collectedErrors.add(exception);
                }
            }
        }
    }

    public List<ValidationException> getParsingWarnings() {
        return parsingWarnings;
    }

    public List<ValidationException> getCollectedErrors() {
        return collectedErrors;
    }

    public boolean hasCollectedErrors() {
        return !collectedErrors.isEmpty();
    }

    /**
     * Throws an AggregatedValidationException if any errors were collected during processing.
     * This should be called at the end of processing when fail-fast mode is disabled.
     */
    public void throwIfErrorsCollected() throws AggregatedValidationException {
        if (!collectedErrors.isEmpty()) {
            throw new AggregatedValidationException(collectedErrors);
        }
    }

    private void handleRuleException(Exception e, RuleSeverity severity, String rule) throws ValidationException {
        Throwable cause = e;
        if (e instanceof InvocationTargetException) {
            cause = e.getCause();
        }
        if (cause instanceof ValidationException ve) {
            if (severity == RuleSeverity.WARN) {
                parsingWarnings.add(new ValidationException(rule, ve.getMessage()));
            } else if (severity == RuleSeverity.ERROR) {
                ValidationException validationException = new ValidationException(rule, ve.getLine(), ve.getMessage());
                handleError(validationException);
            } else {
                handleError(ve);
            }
        } else {
            throw new RuntimeException(cause);
        }
    }

    private void handleError(ValidationException ve) throws ValidationException {
        if (failFast) {
            throw ve;
        } else {
            collectedErrors.add(ve);
        }
    }

    @Override
    public void close() {
        context.close();
    }
}
