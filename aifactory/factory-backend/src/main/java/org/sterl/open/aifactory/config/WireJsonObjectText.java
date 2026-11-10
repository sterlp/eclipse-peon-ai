package org.sterl.open.aifactory.config;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * An extra body the core can select (R-WP-52, R-WP-55): an object or nothing, which is exactly what
 * {@code ExtraBody} keeps — anything else it drops without a trace, so a wrong shape would be stored and
 * answered as if it were applied.
 *
 * <p>The shape is checked here rather than by asking the core because the core <em>logs</em> what it
 * discards: a refusal of a submitted body must never repeat it ({@code aifactory/AGENTS-DEV.md}).</p>
 *
 * <p>Why only {@code FIELD}: {@link WireValidationHints}.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Constraint(validatedBy = WireJsonObjectText.Validator.class)
public @interface WireJsonObjectText {

    String message() default WireValidationHints.EXTRA_BODY_HINT;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<WireJsonObjectText, String> {

        /** Reads the submitted text the way the core selects it — an object or nothing. */
        private static final ObjectMapper BODY_JSON = new ObjectMapper();

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            // absent or blank names nothing (R-WP-16) — clearing a field is never a wrong value
            if (value == null || value.isBlank()) {
                return true;
            }
            try {
                return BODY_JSON.readValue(value, Object.class) instanceof Map;
            } catch (IOException | IllegalArgumentException malformed) {
                return false;
            }
        }
    }
}
