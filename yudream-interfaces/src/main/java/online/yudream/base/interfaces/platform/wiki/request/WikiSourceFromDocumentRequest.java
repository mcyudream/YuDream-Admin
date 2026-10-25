package online.yudream.base.interfaces.platform.wiki.request;

import jakarta.validation.constraints.NotNull;

/** 把编辑器文档一键收录为资料的请求体。 */
public class WikiSourceFromDocumentRequest {

    @NotNull(message = "文档不能为空")
    private Long nodeId;

    public Long getNodeId() {
        return nodeId;
    }

    public void setNodeId(Long nodeId) {
        this.nodeId = nodeId;
    }

    /** 兼容前端直接传 Long 的场景。 */
    public Long nodeIdAsLong() {
        return nodeId;
    }
}
