package online.yudream.base.application.platform.plugin.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpStreamingBody;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpStreamingPart;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 流式 HTTP 分发指令：query/body/parts 以惰性 Supplier 提供，
 * 运行时网关在鉴权与前置限长通过后才物化，避免在鉴权前消费请求体。
 */
@Data
public class PluginHttpStreamingDispatchCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String pluginCode;
    private String method;
    private String path;
    private Map<String, List<String>> headers;
    private Long userId;
    private List<String> permissions;
    private Supplier<Map<String, List<String>>> querySupplier;
    private Supplier<PluginHttpStreamingBody> bodySupplier;
    private Supplier<Map<String, PluginHttpStreamingPart>> partsSupplier;
}
