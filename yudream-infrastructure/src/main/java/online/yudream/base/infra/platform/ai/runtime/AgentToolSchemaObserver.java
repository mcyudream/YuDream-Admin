package online.yudream.base.infra.platform.ai.runtime;

import java.util.List;
import java.util.function.Consumer;

/** 原生线程中记录经过权限和白名单过滤后的真实模型工具面。 */
record AgentToolSchemaObserver(Consumer<List<String>> reporter) {
    void report(List<String> names) { if (reporter != null) reporter.accept(names); }
}
