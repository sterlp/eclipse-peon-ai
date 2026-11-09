package org.sterl.open.aifactory.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * A provider name the core really has (R-WP-55). The core's own {@code AiProvider.parse} is refused on
 * purpose: it coerces an unknown name to {@code OLLAMA}, so a typo would be answered with 200 and
 * stored as garbage. Why only {@code FIELD}: {@link WireValidationHints}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Constraint(validatedBy = WireProviderName.Validator.class)
public @interface WireProviderName {

    String message() default WireValidationHints.PROVIDER_HINT;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<WireProviderName, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            // absent or blank names nothing (R-WP-16) — clearing a field is never a wrong value
            return value == null || value.isBlank() || FactoryConfigService.isProviderName(value.trim());
        }
    }
}
