package online.yudream.base.infra.platform.plugin.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * descriptor 原文以 JSON 字符串存储：PluginStorePluginDescriptor 是无 setter 的不可变值对象，
 * 交给 Mongo 直接映射不可靠，解析始终收敛在网关。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformPluginMarketSourceSnapshot")
public class PluginMarketSourceSnapshotDO extends BaseDO {

    @Indexed(unique = true)
    private Long sourceId;
    private LocalDateTime syncedAt;
    private List<Entry> entries;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        private String code;
        private String indexUrl;
        private String latestDescriptorJson;
        private List<Version> versions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Version {
        private String releaseVersion;
        private String descriptorUrl;
    }
}
