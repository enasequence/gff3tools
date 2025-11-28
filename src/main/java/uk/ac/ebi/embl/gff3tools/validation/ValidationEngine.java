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
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

public class ValidationEngine {
    private static Logger LOG = LoggerFactory.getLogger(ValidationEngine.class);

    private final List<ValidationException> parsingWarnings;
    public ValidationConfig validationConfig;
    public ValidationRegistry validationRegistry;

    ValidationEngine(ValidationConfig validationConfig, ValidationRegistry validationRegistry) {
        this.parsingWarnings = new java.util.ArrayList<>();
        this.validationConfig = validationConfig;
        this.validationRegistry = validationRegistry;
    }

    /**
     * Executes validations and fixes for the passed GFF3Feature ans GFF3Annotation
     */
    public <T> void validate(T target, int line) throws ValidationException {

        executeFixs(target, line);
        executeValidations(target, line);
    }

    public <T> void executeValidations(T target, int line) throws ValidationException {
        List<ValidatorDescriptor> validators = validationRegistry.getValidations();

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

    public <T> void executeFixs(T target, int line) throws ValidationException {
        List<ValidatorDescriptor> validators = validationRegistry.getFixs();

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
        String rule = exception.getValidationRule().toString();
        RuleSeverity severity = validationConfig.getSeverity(rule, RuleSeverity.ERROR);
        switch (severity) {
            case OFF -> {}
            case WARN -> {
                parsingWarnings.add(exception);
                LOG.warn(exception.getMessage());
            }
            case ERROR -> {
                throw exception;
            }
        }
    }

    public List<ValidationException> getParsingWarnings() {
        return parsingWarnings;
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
                throw new ValidationException(rule, ve.getLine(), ve.getMessage());
            } else {
                throw ve;
            }
        } else {
            throw new RuntimeException(cause);
        }
    }
}
