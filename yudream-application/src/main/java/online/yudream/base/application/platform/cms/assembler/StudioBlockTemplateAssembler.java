package online.yudream.base.application.platform.cms.assembler;

import online.yudream.base.application.platform.cms.dto.StudioBlockTemplateDTO;
import online.yudream.base.domain.platform.cms.aggregate.StudioBlockTemplate;

public final class StudioBlockTemplateAssembler {
    private StudioBlockTemplateAssembler() {
    }

    /** 版本与创建人 ID 以字符串输出，避免长整型在前端丢失精度。 */
    public static StudioBlockTemplateDTO toDTO(StudioBlockTemplate template) {
        return new StudioBlockTemplateDTO(template.id(), template.name(), template.category(), template.description(), template.keywords(),
                template.nodesJson(), String.valueOf(template.revision()),
                template.createdBy() == null ? null : String.valueOf(template.createdBy()), template.createdAt(), template.updatedAt());
    }
}
