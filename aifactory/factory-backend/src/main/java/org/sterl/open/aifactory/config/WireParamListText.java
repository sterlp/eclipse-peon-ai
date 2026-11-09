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
 * Comma separated {@code key=value} pairs (R-WP-51, R-WP-55). One segment without {@code =} would reach
 * the core as a variable named after the whole segment with an empty value ({@code parseCsvMap}) —
 * nobody means that, so it is refused instead of stored. A blank segment stays legal: that one the core
 * reads as nothing at all, which is why a trailing comma still saves.
 *
 * <p>Why only {@code FIELD}: {@link WireValidationHints}.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Constraint(validatedBy = WireParamListText.Validator.class)
public @interface WireParamListText {

    String message() default WireValidationHints.QUERY_PARAMS_HINT;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<WireParamListText, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return true;
            }
            for (String segment : value.split(",")) {
                if (!segment.isBlank() && !segment.contains("=")) {
                    return false;
                }
            }
            return true;
        }
    }
}
