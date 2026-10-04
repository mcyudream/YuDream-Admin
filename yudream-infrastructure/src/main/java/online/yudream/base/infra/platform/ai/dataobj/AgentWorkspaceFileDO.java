package online.yudream.base.infra.platform.ai.dataobj;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AgentScope Harness 工作区（RemoteFilesystemSpec）的 MongoDB KV 存储行。
 * storeKey 为「命名空间段 + 文件键」的确定性主键，与框架 InMemoryStore 的复合键语义一致。
 */
@Data
@Document("platformAgentWorkspace")
@CompoundIndexes(@CompoundIndex(name = "workspace_store_key", def = "{'storeKey':1}", unique = true))
public class AgentWorkspaceFileDO {

    /** 命名空间段 + "/" + key 拼接的确定性主键 */
    @Id
    private String storeKey;

    private String namespace;

    private String key;

    /** 文件内容负载（框架约定 Map<String,Object>，Mongo 直接保形存储） */
    private Map<String, Object> value;

    /** 乐观锁版本：put 递增，putIfVersion 按 version 条件更新 */
    private long dataVersion;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
