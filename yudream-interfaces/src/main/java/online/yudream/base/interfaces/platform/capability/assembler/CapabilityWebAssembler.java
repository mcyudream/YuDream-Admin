package online.yudream.base.interfaces.platform.capability.assembler;

import online.yudream.base.application.platform.capability.cmd.CapabilityConfigUpdateCmd;
import online.yudream.base.application.platform.capability.cmd.CapabilityTestCmd;
import online.yudream.base.application.platform.capability.dto.CapabilityDTO;
import online.yudream.base.application.platform.capability.dto.CapabilityTestDTO;
import online.yudream.base.interfaces.platform.capability.request.CapabilityConfigUpdateRequest;
import online.yudream.base.interfaces.platform.capability.request.CapabilityTestRequest;
import online.yudream.base.interfaces.platform.capability.res.CapabilityRes;
import online.yudream.base.interfaces.platform.capability.res.CapabilityTestRes;

import java.util.List;

public class CapabilityWebAssembler {

    public static CapabilityConfigUpdateCmd toCmd(String code, CapabilityConfigUpdateRequest request) {
        CapabilityConfigUpdateCmd cmd = new CapabilityConfigUpdateCmd();
        cmd.setCode(code);
        cmd.setConfig(request.getConfig());
        return cmd;
    }

    public static CapabilityTestCmd toCmd(String code, CapabilityTestRequest request) {
        CapabilityTestCmd cmd = new CapabilityTestCmd();
        cmd.setCode(code);
        cmd.setMessage(request.getMessage());
        return cmd;
    }

    public static List<CapabilityRes> toResList(List<CapabilityDTO> list) {
        return list.stream().map(CapabilityWebAssembler::toRes).toList();
    }

    public static CapabilityRes toRes(CapabilityDTO dto) {
        return CapabilityRes.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .type(dto.getType())
                .description(dto.getDescription())
                .icon(dto.getIcon())
                .sort(dto.getSort())
                .enabled(dto.getEnabled())
                .config(dto.getConfig())
                .status(dto.getStatus())
                .healthMessage(dto.getHealthMessage())
                .checkedAt(dto.getCheckedAt())
                .metrics(dto.getMetrics())
                .build();
    }

    public static CapabilityTestRes toRes(CapabilityTestDTO dto) {
        return CapabilityTestRes.builder()
                .success(dto.getSuccess())
                .message(dto.getMessage())
                .testedAt(dto.getTestedAt())
                .build();
    }
}
