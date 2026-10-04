package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import lombok.RequiredArgsConstructor;

/**
 * MongoDB 版 DistributedStore：一次性向 Harness 注入会话状态存储与工作区 KV 存储，
 * 满足 RemoteFilesystemSpec/SandboxFilesystemSpec 对分布式存储的构建校验。
 */
@RequiredArgsConstructor
public class MongoDistributedStore implements DistributedStore {

    private final AgentStateStore stateStore;
    private final BaseStore baseStore;

    @Override
    public AgentStateStore agentStateStore() {
        return stateStore;
    }

    @Override
    public BaseStore baseStore() {
        return baseStore;
    }
}
