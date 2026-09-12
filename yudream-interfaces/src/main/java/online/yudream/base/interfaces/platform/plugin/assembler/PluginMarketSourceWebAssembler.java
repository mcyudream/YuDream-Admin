package online.yudream.base.interfaces.platform.plugin.assembler;

import online.yudream.base.application.platform.plugin.cmd.PluginMarketSourceCreateCmd;
import online.yudream.base.application.platform.plugin.cmd.PluginMarketSourceTestCmd;
import online.yudream.base.application.platform.plugin.cmd.PluginMarketSourceUpdateCmd;
import online.yudream.base.application.platform.plugin.dto.PluginMarketSourceDTO;
import online.yudream.base.application.platform.plugin.dto.PluginMarketSourceTestResultDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.interfaces.platform.plugin.request.PluginMarketSourceCreateRequest;
import online.yudream.base.interfaces.platform.plugin.request.PluginMarketSourceTestRequest;
import online.yudream.base.interfaces.platform.plugin.request.PluginMarketSourceUpdateRequest;
import online.yudream.base.interfaces.platform.plugin.res.PluginMarketSourceRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginMarketSourceTestResultRes;

public class PluginMarketSourceWebAssembler {

    private PluginMarketSourceWebAssembler() {
    }

    public static PluginMarketSourceCreateCmd toCreateCmd(PluginMarketSourceCreateRequest request) {
        PluginMarketSourceCreateCmd cmd = new PluginMarketSourceCreateCmd();
        cmd.setCode(request.getCode());
        cmd.setName(request.getName());
        cmd.setType(request.getType());
        cmd.setRootUrl(request.getRootUrl());
        cmd.setToken(request.getToken());
        cmd.setSortOrder(request.getSortOrder());
        return cmd;
    }

    public static PluginMarketSourceUpdateCmd toUpdateCmd(Long id, PluginMarketSourceUpdateRequest request) {
        PluginMarketSourceUpdateCmd cmd = new PluginMarketSourceUpdateCmd();
        cmd.setId(id);
        cmd.setName(request.getName());
        cmd.setType(request.getType());
        cmd.setRootUrl(request.getRootUrl());
        cmd.setToken(request.getToken());
        cmd.setSortOrder(request.getSortOrder());
        return cmd;
    }

    public static PluginMarketSourceTestCmd toTestCmd(PluginMarketSourceTestRequest request) {
        PluginMarketSourceTestCmd cmd = new PluginMarketSourceTestCmd();
        cmd.setType(request.getType());
        cmd.setRootUrl(request.getRootUrl());
        cmd.setToken(request.getToken());
        return cmd;
    }

    public static PluginMarketSourceRes toRes(PluginMarketSourceDTO dto) {
        return PluginMarketSourceRes.builder()
                .id(dto.getId() == null ? null : dto.getId().toString())
                .code(dto.getCode())
                .name(dto.getName())
                .type(dto.getType())
                .rootUrl(dto.getRootUrl())
                .tokenConfigured(dto.isTokenConfigured())
                .enabled(dto.isEnabled())
                .builtIn(dto.isBuiltIn())
                .sortOrder(dto.getSortOrder())
                .syncStatus(dto.getSyncStatus())
                .syncErrorMessage(dto.getSyncErrorMessage())
                .syncedAt(dto.getSyncedAt())
                .pluginCount(dto.getPluginCount())
                .build();
    }

    public static java.util.List<PluginMarketSourceRes> toResList(java.util.List<PluginMarketSourceDTO> items) {
        return items == null ? java.util.List.of() : items.stream().map(PluginMarketSourceWebAssembler::toRes).toList();
    }

    public static PluginMarketSourceTestResultRes toTestRes(PluginMarketSourceTestResultDTO dto) {
        return PluginMarketSourceTestResultRes.builder()
                .ok(dto.isOk())
                .pluginCount(dto.getPluginCount())
                .message(dto.getMessage())
                .build();
    }

    /** 路径参数 id 统一在此解析为 Long，控制器不做类型转换细节。 */
    public static Long parseId(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            throw new BizException("市场源不存在");
        }
    }
}
