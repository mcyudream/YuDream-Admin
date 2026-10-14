package online.yudream.base.plugin.spi.system.ai;

/** 兼容一次性调用遇到原生审批时明确暂停，不能把部分回答误当业务成功。 */
public final class PluginAiInteractionRequiredException extends RuntimeException {
    private final String runId;

    public PluginAiInteractionRequiredException(String runId) {
        super("AI 运行等待用户确认，请通过持久任务继续：" + runId);
        this.runId = runId;
    }

    public String runId() { return runId; }
}
