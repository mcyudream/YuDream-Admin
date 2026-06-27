package online.yudream.base.interfaces.common.validation.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import online.yudream.base.domain.valobj.Password;
import online.yudream.base.interfaces.common.validation.anno.PasswordRule;

public class PasswordValidator implements ConstraintValidator<PasswordRule, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;  // 非空校验交给 @NotBlank
        }
        try {
            Password.validate(value);  // 复用 Domain 层规则
            return true;
        } catch (IllegalArgumentException e) {
            // 禁用默认消息，使用 Domain 抛出的具体错误
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(e.getMessage())
                    .addConstraintViolation();
            return false;
        }
    }
}
