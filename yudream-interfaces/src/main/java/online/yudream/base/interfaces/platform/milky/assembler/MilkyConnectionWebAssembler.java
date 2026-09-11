package online.yudream.base.interfaces.platform.milky.assembler;

import online.yudream.base.application.platform.milky.cmd.MilkyConnectionCreateCmd;
import online.yudream.base.application.platform.milky.cmd.MilkyConnectionUpdateCmd;
import online.yudream.base.application.platform.milky.dto.MilkyConnectionDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.platform.milky.request.MilkyConnectionCreateRequest;
import online.yudream.base.interfaces.platform.milky.request.MilkyConnectionUpdateRequest;
import online.yudream.base.interfaces.platform.milky.res.MilkyConnectionRes;

public final class MilkyConnectionWebAssembler {
    private MilkyConnectionWebAssembler() { }

    public static MilkyConnectionCreateCmd toCmd(MilkyConnectionCreateRequest request) {
        MilkyConnectionCreateCmd cmd = new MilkyConnectionCreateCmd();
        cmd.setName(request.getName());
        cmd.setProtocol(request.getProtocol());
        cmd.setBaseUrl(request.getBaseUrl());
        cmd.setToken(request.getToken());
        cmd.setAppId(request.getAppId());
        cmd.setAppSecret(request.getAppSecret());
        cmd.setSandbox(request.getSandbox());
        cmd.setIntents(request.getIntents());
        cmd.setCommandMenuImageMode(request.getCommandMenuImageMode());
        cmd.setCommandMenuPublicBaseUrl(request.getCommandMenuPublicBaseUrl());
        return cmd;
    }

    public static MilkyConnectionUpdateCmd toCmd(Long id, MilkyConnectionUpdateRequest request) {
        MilkyConnectionUpdateCmd cmd = new MilkyConnectionUpdateCmd();
        cmd.setId(id);
        cmd.setName(request.getName());
        cmd.setProtocol(request.getProtocol());
        cmd.setBaseUrl(request.getBaseUrl());
        cmd.setToken(request.getToken());
        cmd.setAppId(request.getAppId());
        cmd.setAppSecret(request.getAppSecret());
        cmd.setSandbox(request.getSandbox());
        cmd.setIntents(request.getIntents());
        cmd.setCommandMenuImageMode(request.getCommandMenuImageMode());
        cmd.setCommandMenuPublicBaseUrl(request.getCommandMenuPublicBaseUrl());
        return cmd;
    }

    public static MilkyConnectionRes toRes(MilkyConnectionDTO dto) {
        return MilkyConnectionRes.builder()
                .id(dto.getId() == null ? null : String.valueOf(dto.getId()))
                .name(dto.getName())
                .protocol(dto.getProtocol())
                .baseUrl(dto.getBaseUrl())
                .appId(dto.getAppId())
                .sandbox(dto.isSandbox())
                .intents(dto.getIntents())
                .mentionOpenIds(dto.getMentionOpenIds())
                .enabled(dto.isEnabled())
                .credentialConfigured(dto.isCredentialConfigured())
                .commandMenuImageMode(dto.getCommandMenuImageMode())
                .commandMenuPublicBaseUrl(dto.getCommandMenuPublicBaseUrl())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static PageResult<MilkyConnectionRes> toRes(PageResult<MilkyConnectionDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(MilkyConnectionWebAssembler::toRes).toList(),
                page.getTotal(), page.getPage(), page.getSize());
    }
}
