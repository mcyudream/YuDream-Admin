package online.yudream.base.infra.platform.agent.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.service.AgentRuntimeApplicationRegistry;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class PluginAgentApplicationRegistry implements AgentRuntimeApplicationRegistry {
    private final ConcurrentMap<String, Registration> applications = new ConcurrentHashMap<>();

    @Override
    public AutoCloseable register(String ownerCode, AgentApplication application) {
        if (!StringUtils.hasText(ownerCode) || application == null || !StringUtils.hasText(application.getCode())) {
            throw new BizException("插件 Agent 定义无效");
        }
        Registration registration = new Registration(ownerCode.trim(), application);
        Registration existing = applications.putIfAbsent(application.getCode(), registration);
        if (existing != null) {
            throw new BizException("运行时 Agent 编码重复：" + application.getCode());
        }
        return () -> applications.remove(application.getCode(), registration);
    }

    @Override
    public Optional<AgentApplication> findByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        return Optional.ofNullable(applications.get(code.trim().toLowerCase()))
                .map(Registration::application);
    }

    @Override
    public Optional<String> ownerCode(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        return Optional.ofNullable(applications.get(code.trim().toLowerCase()))
                .map(Registration::ownerCode);
    }

    @Override
    public List<AgentApplication> applications() {
        return applications.values().stream()
                .map(Registration::application)
                .sorted(Comparator.comparing(AgentApplication::getCode))
                .toList();
    }

    private record Registration(String ownerCode, AgentApplication application) {
    }
}
