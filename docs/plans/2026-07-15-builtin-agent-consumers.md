# 内置 Agent 与业务消费方迁移实施计划

1. 为 SPI Agent 目录和执行契约编写编译测试，增加稳定 DTO 与 `PluginAiService` 方法，并在 core 插件适配器接入 `AgentAppService`。
2. 为内置 Agent 初始化编写幂等、默认模型和无模型草稿测试，实现 CMS Builder 与群聊机器人应用初始化器。
3. 为 CMS Agent 请求映射、目录选择和 AG-UI 回调编写测试，将 `AiAppService` 的 CMS 生成委托给指定 Agent，并把前端模型下拉替换为 Agent 应用下拉。
4. 在独立插件仓库为群聊策略 `agentCode` 迁移、Agent 执行和选项接口编写测试，替换 Provider/模型字段与前端控件。
5. 分别运行 core Maven/前端、SPI、插件 Maven/前端验证，执行 DDD 扫描，提交独立中文提交并保留用户未提交文件。
