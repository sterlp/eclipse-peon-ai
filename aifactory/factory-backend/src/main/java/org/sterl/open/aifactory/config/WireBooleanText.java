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
 * The three states of a checkbox on a text wire (R-WP-16, R-WP-55): only the two words the core reads
 * as a boolean are accepted — anything else would be stored and read back as {@code false}, which is a
 * value nobody typed. Case is the core's own tolerance, so {@code TRUE} stays legal and is stored
 * normalized. Why only {@code FIELD}: {@link WireValidationHints}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Constraint(validatedBy = WireBooleanText.Validator.class)
public @interface WireBooleanText {

    String message() default WireValidationHints.SEND_THINKING_HINT;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<WireBooleanText, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return true;
            }
            String text = value.trim();
            return "true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text);
        }
    }
}
