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
 * A body that sets a key and asks to delete it in the same breath is no answer at all: applied in write
 * order it deletes the key it just set, so the request is refused rather than guessed at (UC-WP-63,
 * R-WP-55). It reads {@link KeyIntentRequest}, so the config level and the agent level state it once.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Constraint(validatedBy = WireKeyIntent.Validator.class)
public @interface WireKeyIntent {

    String message() default WireValidationHints.KEY_INTENT_HINT;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** The field this rule speaks about — a refusal of the whole body still has to reach it (UC-WP-92). */
    String FIELD = "apiKey";

    class Validator implements ConstraintValidator<WireKeyIntent, KeyIntentRequest> {

        private String message;

        @Override
        public void initialize(WireKeyIntent constraint) {
            this.message = constraint.message();
        }

        @Override
        public boolean isValid(KeyIntentRequest request, ConstraintValidatorContext context) {
            if (request == null || !Boolean.TRUE.equals(request.removeApiKey())
                    || request.apiKey() == null || request.apiKey().isBlank()) {
                return true;
            }
            // the rule stands on the body, the answer belongs to the key field: an empty path would be
            // "no field", and the UI could only show it as flowing text
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(message).addPropertyNode(FIELD).addConstraintViolation();
            return false;
        }
    }
}
