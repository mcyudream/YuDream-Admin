package online.yudream.base.domain.platform.feishu.service;

import online.yudream.base.domain.platform.feishu.valobj.FeishuBlockWriteResult;
import online.yudream.base.domain.platform.feishu.valobj.FeishuCloudDoc;
import online.yudream.base.domain.platform.feishu.valobj.FeishuDocPage;
import online.yudream.base.domain.platform.feishu.valobj.FeishuDocumentBlock;
import online.yudream.base.domain.platform.feishu.valobj.FeishuDocumentMeta;
import online.yudream.base.domain.platform.feishu.valobj.FeishuWikiNode;
import online.yudream.base.domain.platform.feishu.valobj.FeishuWikiSpace;

import java.util.List;

/**
 * 飞书云文档网关（本工作项唯一在范围的 API 模块）。
 * <p>
 * 契约边界：认证（tenant_access_token 获取与缓存）属于实现细节，不在本接口暴露；
 * 每个方法对应一次云文档 API 请求，因此应用层每次调用都必须先完成额度预占。
 * 返回值的 {@code raw} 字段必须原样携带飞书 JSON，实现不得做有损改写。
 */
public interface FeishuCloudDocGateway {

    FeishuDocPage<FeishuCloudDoc> listDriveFiles(String folderToken, String pageToken, int pageSize);

    FeishuDocPage<FeishuWikiSpace> listWikiSpaces(String pageToken, int pageSize);

    /**
     * 知识库节点列表（wiki/v2/spaces/{space_id}/nodes）。
     *
     * @param parentNodeToken 父节点 token；空表示根级
     * @param pageToken       分页游标；空表示第一页
     * @param pageSize        期望条数，实现钳制到飞书上限 50
     */
    FeishuDocPage<FeishuWikiNode> listWikiNodes(String spaceId, String parentNodeToken, String pageToken, int pageSize);

    FeishuDocumentMeta documentMeta(String documentId);

    /** 文档纯文本内容，原样返回飞书 raw_content。 */
    String documentRawContent(String documentId, Integer lang);

    FeishuDocPage<FeishuDocumentBlock> documentBlocks(String documentId, String pageToken, int pageSize);

    /** 在父块下追加/插入子块：childrenJson 为飞书块 JSON 原文，保真提交。 */
    FeishuBlockWriteResult appendDocumentBlocks(String documentId, String parentBlockId, List<String> childrenJson, Integer index);

    /**
     * 删除父块下 [startIndex, endIndex) 区间的子块：左闭右开，与飞书 batch_delete 官方语义一致，
     * 实现必须原样透传，不做下标换算。
     */
    void deleteDocumentBlocks(String documentId, String parentBlockId, int startIndex, int endIndex);

    /** 更新文本承载块的 elements（飞书 update_text_elements 操作），elementsJson 为元素 JSON 原文。 */
    FeishuBlockWriteResult updateBlockElements(String documentId, String blockId, List<String> elementsJson, Long documentRevisionId);

    /** 显式连接测试：获取 tenant_access_token 并返回可读结果，仅由能力测试动作触发。 */
    String probe();
}
