package online.yudream.base.application.platform.milky.assembler;

import online.yudream.base.application.platform.milky.dto.MilkyConnectionDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
public final class MilkyConnectionAssembler {
    private MilkyConnectionAssembler() { }
    public static MilkyConnectionDTO toDTO(MilkyConnection source) { return source == null ? null : MilkyConnectionDTO.builder().id(source.getId()).name(source.getName()).baseUrl(source.getBaseUrl()).enabled(source.isEnabled()).credentialConfigured(source.getToken() != null && !source.getToken().isBlank()).commandMenuImageMode(source.getCommandMenuImageMode()).commandMenuPublicBaseUrl(source.getCommandMenuPublicBaseUrl()).createTime(source.getCreateTime()).updateTime(source.getUpdateTime()).build(); }
    public static PageResult<MilkyConnectionDTO> toDTO(PageResult<MilkyConnection> source) { return new PageResult<>(source.getRecords().stream().map(MilkyConnectionAssembler::toDTO).toList(), source.getTotal(), source.getPage(), source.getSize()); }
}
