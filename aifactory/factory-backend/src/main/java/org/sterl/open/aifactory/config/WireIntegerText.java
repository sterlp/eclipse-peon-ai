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
 * A whole number the core can read back (R-WP-51, R-WP-55). The accepted range is the range the core
 * parses: {@code LlmConfigLoader.parseInt} turns garbage into 0, which there means "provider default",
 * so a value it cannot read has to be refused on the wire, where an answer can still name the field —
 * anything else would be stored happily and answer later with a number nobody typed.
 *
 * <p>Why only {@code FIELD}: {@link WireValidationHints}.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Constraint(validatedBy = WireIntegerText.Validator.class)
public @interface WireIntegerText {

    String message() default WireValidationHints.TOKEN_WINDOW_HINT;

    long min() default 1;

    long max() default Long.MAX_VALUE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<WireIntegerText, String> {

        private long min;
        private long max;

        @Override
        public void initialize(WireIntegerText constraint) {
            this.min = constraint.min();
            this.max = constraint.max();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return true;
            }
            try {
                long number = Long.parseLong(value.trim());
                return number >= min && number <= max;
            } catch (NumberFormatException notANumber) {
                return false;
            }
        }
    }
}
