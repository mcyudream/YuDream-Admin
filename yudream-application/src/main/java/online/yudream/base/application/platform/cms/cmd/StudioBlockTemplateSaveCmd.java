package online.yudream.base.application.platform.cms.cmd;

import java.util.List;

/** 创建或更新区块模板；更新时 expectedRevision 必填，nodesJson 为空表示只改元数据。 */
public record StudioBlockTemplateSaveCmd(String name, String category, String description, List<String> keywords,
                                         String nodesJson, String expectedRevision) {}
