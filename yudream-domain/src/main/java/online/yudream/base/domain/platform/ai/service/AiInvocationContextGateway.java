package online.yudream.base.domain.platform.ai.service;

import online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot;
import java.util.List;

/** 运行来源的技术作用域由适配器恢复，应用与领域不依赖插件 SPI 或 QQ ThreadLocal。 */
public interface AiInvocationContextGateway {
    AiInvocationContextSnapshot capture();
    Scope restore(AiInvocationContextSnapshot snapshot, Long nativeUserId, List<String> freshPermissions);
    interface Scope extends AutoCloseable { @Override void close(); }
}
