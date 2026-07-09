package online.yudream.base.plugin.projectprogress.application.service;

import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectProgressProject;
import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectWorkDetail;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.mail.PluginMailMessage;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;

import java.util.List;

public class ProjectProgressNotificationService {

    private final FrameworkServices framework;

    public ProjectProgressNotificationService(FrameworkServices framework) {
        this.framework = framework;
    }

    public void notifyAssigned(ProjectProgressProject project, ProjectWorkDetail detail, List<String> userIds) {
        send(userIds, "项目任务分配通知", "你被分配了项目任务：" + project.name() + " / " + detail.title());
    }

    public void notifyRework(ProjectProgressProject project, ProjectWorkDetail detail, List<String> userIds, String reason) {
        send(userIds, "项目任务返工通知", "项目任务未通过验收，请返工：" + project.name() + " / " + detail.title()
                + (reason == null || reason.isBlank() ? "" : "\n原因：" + reason));
    }

    private void send(List<String> userIds, String subject, String text) {
        if (framework == null || framework.mail() == null || userIds == null || userIds.isEmpty()) {
            return;
        }
        for (String userId : userIds) {
            email(userId).ifPresent(email -> framework.mail().send(PluginMailMessage.text(List.of(email), subject, text)));
        }
    }

    private java.util.Optional<String> email(String userId) {
        if (framework == null || framework.users() == null || userId == null || userId.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return framework.users().findById(Long.parseLong(userId.trim()))
                    .map(PluginUserProfile::email)
                    .filter(value -> value != null && !value.isBlank());
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }
}
