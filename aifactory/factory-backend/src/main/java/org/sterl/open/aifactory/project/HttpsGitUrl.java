package org.sterl.open.aifactory.project;

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
 * A public {@code https} git url and nothing else (R-WP-20): ssh, the scp-like form and a local path are
 * refused before git, the registry file or a folder can see them (UC-WP-30). The refusal is the very
 * sentence {@link ProjectService#requirePublicHttpsUrl} throws, so the wire and any other caller of that
 * rule answer with the same words — neither of them naming a user, a host or a token (UC-WP-92).
 *
 * <p>An empty value is {@code @NotBlank}'s case and returns valid here: one wrong value is one entry in
 * the answer, and a missing url is stated once, by the annotation that means "not blank".</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Constraint(validatedBy = HttpsGitUrl.Validator.class)
public @interface HttpsGitUrl {

    /** Only as documentation of the common case — the validator answers with the registry's own sentence. */
    String message() default ProjectService.HTTPS_ONLY_HINT;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<HttpsGitUrl, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return true;
            }
            try {
                ProjectService.requirePublicHttpsUrl(value.trim()); // trimmed like the registry does
                return true;
            } catch (IllegalArgumentException refused) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(refused.getMessage()).addConstraintViolation();
                return false;
            }
        }
    }
}
