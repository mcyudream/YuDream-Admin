package online.yudream.base.interfaces.platform.satori.assembler;

import online.yudream.base.application.platform.satori.cmd.SatoriConnectionCreateCmd;
import online.yudream.base.application.platform.satori.cmd.SatoriConnectionPageQuery;
import online.yudream.base.application.platform.satori.cmd.SatoriConnectionUpdateCmd;
import online.yudream.base.application.platform.satori.dto.SatoriConnectionDTO;
import online.yudream.base.application.platform.satori.dto.SatoriConnectionTestDTO;
import online.yudream.base.application.platform.satori.dto.SatoriOperationLogDTO;
import online.yudream.base.application.platform.satori.dto.SatoriEventDetailDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.platform.satori.request.SatoriConnectionCreateRequest;
import online.yudream.base.interfaces.platform.satori.request.SatoriConnectionUpdateRequest;
import online.yudream.base.interfaces.platform.satori.res.SatoriConnectionRes;
import online.yudream.base.interfaces.platform.satori.res.SatoriConnectionTestRes;
import online.yudream.base.interfaces.platform.satori.res.SatoriOperationLogRes;
import online.yudream.base.interfaces.platform.satori.res.SatoriEventDetailRes;

public final class SatoriConnectionWebAssembler {
    private SatoriConnectionWebAssembler() {
    }

    public static SatoriConnectionCreateCmd toCmd(SatoriConnectionCreateRequest request) {
        SatoriConnectionCreateCmd cmd = new SatoriConnectionCreateCmd();
        cmd.setName(request.getName());
        cmd.setBaseUrl(request.getBaseUrl());
        cmd.setPlatform(request.getPlatform());
        cmd.setUserId(request.getUserId());
        cmd.setToken(request.getToken());
        return cmd;
    }

    public static SatoriConnectionUpdateCmd toCmd(Long id, SatoriConnectionUpdateRequest request) {
        SatoriConnectionUpdateCmd cmd = new SatoriConnectionUpdateCmd();
        cmd.setId(id);
        cmd.setName(request.getName());
        cmd.setBaseUrl(request.getBaseUrl());
        cmd.setPlatform(request.getPlatform());
        cmd.setUserId(request.getUserId());
        cmd.setToken(request.getToken());
        return cmd;
    }

    public static SatoriConnectionPageQuery toQuery(String keyword, int page, int size) {
        SatoriConnectionPageQuery query = new SatoriConnectionPageQuery();
        query.setKeyword(keyword);
        query.setPage(page);
        query.setSize(size);
        return query;
    }

    public static PageResult<SatoriConnectionRes> toRes(PageResult<SatoriConnectionDTO> result) {
        return new PageResult<>(result.getRecords().stream().map(SatoriConnectionWebAssembler::toRes).toList(),
                result.getTotal(), result.getPage(), result.getSize());
    }

    public static SatoriConnectionRes toRes(SatoriConnectionDTO dto) {
        return SatoriConnectionRes.builder()
                .id(dto.getId() == null ? null : String.valueOf(dto.getId()))
                .name(dto.getName())
                .baseUrl(dto.getBaseUrl())
                .platform(dto.getPlatform())
                .userId(dto.getUserId())
                .enabled(dto.isEnabled())
                .credentialConfigured(dto.isCredentialConfigured())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static SatoriConnectionTestRes toRes(SatoriConnectionTestDTO dto) {
        return SatoriConnectionTestRes.builder()
                .success(dto.isSuccess())
                .platform(dto.getPlatform())
                .userId(dto.getUserId())
                .status(dto.getStatus())
                .adapter(dto.getAdapter())
                .features(dto.getFeatures())
                .testedAt(dto.getTestedAt())
                .build();
    }

    public static PageResult<SatoriOperationLogRes> toLogRes(PageResult<SatoriOperationLogDTO> result) {
        return new PageResult<>(result.getRecords().stream().map(SatoriConnectionWebAssembler::toLogRes).toList(),
                result.getTotal(), result.getPage(), result.getSize());
    }

    public static SatoriOperationLogRes toLogRes(SatoriOperationLogDTO dto) {
        return SatoriOperationLogRes.builder().id(dto.getId() == null ? null : String.valueOf(dto.getId())).level(dto.getLevel())
                .category(dto.getCategory()).action(dto.getAction()).detail(dto.getDetail()).occurredAt(dto.getOccurredAt()).build();
    }

    public static SatoriEventDetailRes toRes(SatoriEventDetailDTO dto) {
        return SatoriEventDetailRes.builder().sequence(dto.getSequence()).type(dto.getType()).rawData(dto.getRawData())
                .receivedAt(dto.getReceivedAt()).build();
    }
}
