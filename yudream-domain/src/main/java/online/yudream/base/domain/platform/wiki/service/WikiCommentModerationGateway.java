package online.yudream.base.domain.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.valobj.WikiCommentModerationVerdict;

/**
 * 评论机器审核端口：按知识库配置的问答模型判定一条评论能否直接公开。
 * <p>
 * 约束：
 * - 「判定不通过」与「没审成」必须分开：后者抛异常，由应用层记为 unavailable 并回落人工审核，
 *   不能当成拒绝，更不能阻断读者提交；
 * - 判定与理由只作为审计与人工复核依据，最终仍可由人工隐藏/删除/恢复；
 * - 端口不接触评论状态机，状态迁移由聚合在领域内完成。
 */
public interface WikiCommentModerationGateway {

    /**
     * 审核一条评论。
     *
     * @param providerCode 空间配置的问答模型供应商（与 modelCode 成对）
     * @param modelCode    空间配置的问答模型
     * @param spaceName    知识库名称（提示上下文）
     * @param pageTitle    评论所在页面标题（提示上下文）
     * @param quoted       引用的正文原文，无引用时为空串
     * @param content      评论正文
     * @return 判定结果；无法判定时抛异常
     */
    WikiCommentModerationVerdict review(String providerCode, String modelCode, String spaceName,
                                        String pageTitle, String quoted, String content);
}
