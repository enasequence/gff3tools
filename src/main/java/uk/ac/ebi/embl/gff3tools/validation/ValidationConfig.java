package uk.ac.ebi.embl.gff3tools.validation;

import java.util.Map;

public class ValidationConfig {
    private final Map<String, RuleSeverity> ruleOverrides;
    private final Map<String, Boolean> validatorOverrides;

    public ValidationConfig(Map<String, RuleSeverity> ruleOverrides,
                            Map<String, Boolean> validatorOverrides) {
        this.ruleOverrides = ruleOverrides != null ? ruleOverrides : Map.of();
        this.validatorOverrides = validatorOverrides != null ? validatorOverrides : Map.of();
    }

    public RuleSeverity getSeverity(String rule, RuleSeverity defaultAction) {
        return ruleOverrides.getOrDefault(rule, defaultAction);
    }

    public boolean isValidatorEnabled(String validatorName, boolean defaultEnabled) {
        return validatorOverrides.getOrDefault(validatorName, defaultEnabled);
    }
}
