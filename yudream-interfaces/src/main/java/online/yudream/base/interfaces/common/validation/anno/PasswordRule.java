package online.yudream.base.interfaces.common.validation.anno;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import online.yudream.base.interfaces.common.validation.validator.PasswordValidator;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordRule {

    String message() default "密码格式不符合要求";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}