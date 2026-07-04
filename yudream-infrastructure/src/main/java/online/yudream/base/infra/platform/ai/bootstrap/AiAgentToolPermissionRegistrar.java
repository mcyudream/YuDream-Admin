package online.yudream.base.infra.platform.ai.bootstrap;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.system.security.PermissionMeta;
import online.yudream.base.domain.system.user.service.PermissionDomainService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiAgentToolPermissionRegistrar implements ApplicationListener<ApplicationReadyEvent> {

    private final List<AiAgentTool> tools;
    private final PermissionDomainService permissionDomainService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        permissionDomainService.upsertManualPermissions(tools.stream()
                .map(AiAgentTool::descriptor)
                .filter(descriptor -> StringUtils.hasText(descriptor.permissionCode()))
                .map(this::toPermissionMeta)
                .toList());
    }

    private PermissionMeta toPermissionMeta(AiAgentToolDescriptor descriptor) {
        return new PermissionMeta(
                descriptor.permissionCode(),
                descriptor.permissionName(),
                descriptor.permissionModule(),
                descriptor.permissionDesc()
        );
    }
}
