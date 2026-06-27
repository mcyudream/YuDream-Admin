package online.yudream.base.domain.system.mail.repo;

public interface MailTemplateEngine {
    String render(String templateId, java.util.Map<String, Object> variables);
}
