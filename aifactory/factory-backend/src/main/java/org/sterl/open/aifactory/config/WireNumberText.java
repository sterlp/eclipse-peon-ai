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
 * A number that arrives as text, so the three states of R-WP-16 stay apart on the wire (R-WP-55). The
 * core reads a temperature as a number and only <em>warns</em> when it cannot ({@code AgentTemperature}),
 * so a value it cannot parse would be stored happily and ignored silently at turn time — the 400 that
 * names the field is the only answer that tells the page anything.
 *
 * <p>No range is stated here on purpose: the factory refuses what the core cannot read, and nothing more
 * — a range of its own would be a second opinion about another component's field.</p>
 *
 * <p>Why only {@code FIELD}: {@link WireValidationHints}.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Constraint(validatedBy = WireNumberText.Validator.class)
public @interface WireNumberText {

    String message() default WireValidationHints.TEMPERATURE_HINT;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<WireNumberText, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            // absent or blank names nothing (R-WP-16) — clearing a field is never a wrong value
            if (value == null || value.isBlank()) {
                return true;
            }
            try {
                Double.parseDouble(value.trim()); // the core's own reading of the field
                return true;
            } catch (NumberFormatException notANumber) {
                return false;
            }
        }
    }
}
