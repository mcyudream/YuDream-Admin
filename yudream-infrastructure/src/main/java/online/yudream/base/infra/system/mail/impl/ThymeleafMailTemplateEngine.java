package online.yudream.base.infra.system.mail.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.mail.repo.MailTemplateEngine;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ThymeleafMailTemplateEngine implements MailTemplateEngine {

    private final TemplateEngine templateEngine;

    @Override
    public String render(String templateId, Map<String, Object> variables) {
        Context context = new Context();
        if (variables != null) {
            context.setVariables(variables);
        }
        // 模板路径前缀为 mail/，如 welcome -> mail/welcome
        return templateEngine.process("mail/" + templateId, context);
    }
}
