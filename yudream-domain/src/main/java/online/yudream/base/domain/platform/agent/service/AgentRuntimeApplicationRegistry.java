package online.yudream.base.domain.platform.agent.service;

import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;

import java.util.List;
import java.util.Optional;

public interface AgentRuntimeApplicationRegistry {
    AutoCloseable register(String ownerCode, AgentApplication application);

    Optional<AgentApplication> findByCode(String code);

    List<AgentApplication> applications();
}
