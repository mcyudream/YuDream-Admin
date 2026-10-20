package online.yudream.base.infra.platform.cms.mapper;

import online.yudream.base.domain.platform.cms.aggregate.StudioBlockTemplate;
import online.yudream.base.infra.platform.cms.dataobj.StudioBlockTemplateDO;

public final class StudioBlockTemplateInfraMapper {
    private StudioBlockTemplateInfraMapper() {
    }

    public static StudioBlockTemplate toDomain(StudioBlockTemplateDO data) {
        if (data == null) {
            return null;
        }
        return new StudioBlockTemplate(data.getId(), data.getName(), data.getCategory(), data.getDescription(), data.getKeywords(),
                data.getNodesJson(), data.getRevision(), data.getCreatedBy(), data.getCreatedAt(), data.getUpdatedAt());
    }

    public static StudioBlockTemplateDO toData(StudioBlockTemplate template) {
        StudioBlockTemplateDO data = new StudioBlockTemplateDO();
        data.setId(template.id());
        data.setName(template.name());
        data.setCategory(template.category());
        data.setDescription(template.description());
        data.setKeywords(template.keywords());
        data.setNodesJson(template.nodesJson());
        data.setRevision(template.revision());
        data.setCreatedBy(template.createdBy());
        data.setCreatedAt(template.createdAt());
        data.setUpdatedAt(template.updatedAt());
        return data;
    }
}
