package online.yudream.base.plugin.minecraft.interfaces.assembler;

import online.yudream.base.plugin.minecraft.application.cmd.MinecraftPlayerEventCmd;
import online.yudream.base.plugin.minecraft.application.cmd.MinecraftSeasonOpenCmd;
import online.yudream.base.plugin.minecraft.application.cmd.MinecraftServerSaveCmd;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftEconomyRecordDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftEndpointStatusDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftInheritanceRuleDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftSeasonAdjustmentDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftSeasonOperationDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftPlayerActivityDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftServerDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftServerStatusDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftStatusSnapshotDTO;
import online.yudream.base.plugin.minecraft.interfaces.request.MinecraftPlayerEventRequest;
import online.yudream.base.plugin.minecraft.interfaces.request.MinecraftSeasonOpenRequest;
import online.yudream.base.plugin.minecraft.interfaces.request.MinecraftServerSaveRequest;
import online.yudream.base.plugin.minecraft.interfaces.res.MinecraftEconomyRecordRes;
import online.yudream.base.plugin.minecraft.interfaces.res.MinecraftEndpointStatusRes;
import online.yudream.base.plugin.minecraft.interfaces.res.MinecraftInheritanceRuleRes;
import online.yudream.base.plugin.minecraft.interfaces.res.MinecraftSeasonAdjustmentRes;
import online.yudream.base.plugin.minecraft.interfaces.res.MinecraftSeasonOperationRes;
import online.yudream.base.plugin.minecraft.interfaces.res.MinecraftPlayerActivityRes;
import online.yudream.base.plugin.minecraft.interfaces.res.MinecraftServerRes;
import online.yudream.base.plugin.minecraft.interfaces.res.MinecraftServerStatusRes;
import online.yudream.base.plugin.minecraft.interfaces.res.MinecraftStatusSnapshotRes;

public class MinecraftServerWebAssembler {

    public MinecraftServerSaveCmd toCmd(MinecraftServerSaveRequest request) {
        return new MinecraftServerSaveCmd(
                request.id(),
                request.name(),
                request.descriptionMarkdown(),
                request.enabled(),
                request.sort(),
                request.endpoints() == null ? java.util.List.of() : request.endpoints().stream().map(this::toCmd).toList(),
                request.seasons() == null ? java.util.List.of() : request.seasons().stream().map(this::toCmd).toList()
        );
    }

    public MinecraftSeasonOpenCmd toCmd(MinecraftSeasonOpenRequest request) {
        return new MinecraftSeasonOpenCmd(
                request.name(),
                request.description(),
                request.startedAt(),
                request.remark(),
                request.rules() == null ? java.util.List.of() : request.rules().stream().map(this::toCmd).toList()
        );
    }

    public MinecraftPlayerEventCmd toCmd(MinecraftPlayerEventRequest request) {
        return new MinecraftPlayerEventCmd(
                textOr(request.playerId(), request.uuid()),
                textOr(request.playerName(), request.name()),
                request.eventAt()
        );
    }

    public MinecraftServerRes toRes(MinecraftServerDTO dto) {
        return new MinecraftServerRes(
                dto.id(),
                dto.name(),
                dto.descriptionMarkdown(),
                dto.enabled(),
                dto.sort(),
                dto.endpoints().stream().map(this::toRes).toList(),
                dto.seasons().stream().map(this::toRes).toList(),
                dto.currentSeason() == null ? null : toRes(dto.currentSeason()),
                dto.status() == null ? null : toRes(dto.status()),
                dto.createdAt(),
                dto.updatedAt()
        );
    }

    public MinecraftServerStatusRes toRes(MinecraftServerStatusDTO dto) {
        return new MinecraftServerStatusRes(dto.serverId(), dto.status(), dto.onlinePlayers(), dto.maxPlayers(),
                dto.endpoints().stream().map(this::toRes).toList(), dto.checkedAt());
    }

    public MinecraftStatusSnapshotRes toRes(MinecraftStatusSnapshotDTO dto) {
        return new MinecraftStatusSnapshotRes(dto.id(), dto.serverId(), dto.status(), dto.onlinePlayers(),
                dto.maxPlayers(), dto.checkedAt());
    }

    public MinecraftSeasonOperationRes toRes(MinecraftSeasonOperationDTO dto) {
        return new MinecraftSeasonOperationRes(
                dto.id(),
                dto.serverId(),
                dto.fromSeasonId(),
                dto.toSeasonId(),
                dto.toSeasonName(),
                dto.status(),
                dto.rules().stream().map(this::toRes).toList(),
                dto.adjustments().stream().map(this::toRes).toList(),
                dto.operatorUserId(),
                dto.remark(),
                dto.createdAt(),
                dto.rolledBackAt()
        );
    }

    public MinecraftEconomyRecordRes toRes(MinecraftEconomyRecordDTO dto) {
        return new MinecraftEconomyRecordRes(dto.id(), dto.type(), dto.source(), dto.status(), dto.assetCode(),
                dto.amount(), dto.businessNo(), dto.remark(), dto.createdAt());
    }

    public MinecraftPlayerActivityRes toRes(MinecraftPlayerActivityDTO dto) {
        return new MinecraftPlayerActivityRes(dto.serverId(), dto.playerId(), dto.playerName(), dto.online(), dto.afk(),
                dto.totalOnlineMillis(), dto.totalAfkMillis(), dto.currentOnlineSince(), dto.currentAfkSince(),
                dto.lastJoinedAt(), dto.lastQuitAt(), dto.updatedAt());
    }

    private MinecraftServerSaveCmd.Endpoint toCmd(MinecraftServerSaveRequest.Endpoint request) {
        return new MinecraftServerSaveCmd.Endpoint(request.id(), request.name(), request.host(), request.port(),
                request.edition(), request.primaryLine(), request.enabled(), request.sort());
    }

    private MinecraftServerSaveCmd.Season toCmd(MinecraftServerSaveRequest.Season request) {
        return new MinecraftServerSaveCmd.Season(request.id(), request.name(), request.description(), request.startedAt(),
                request.endedAt(), request.current(), request.sort());
    }

    private MinecraftSeasonOpenCmd.Rule toCmd(MinecraftSeasonOpenRequest.Rule request) {
        return new MinecraftSeasonOpenCmd.Rule(request.assetPattern(), request.minAmount(), request.maxAmount(), request.inheritRate());
    }

    private MinecraftServerRes.EndpointRes toRes(MinecraftServerDTO.EndpointDTO dto) {
        return new MinecraftServerRes.EndpointRes(dto.id(), dto.name(), dto.host(), dto.port(), dto.edition(),
                dto.primaryLine(), dto.enabled(), dto.sort());
    }

    private MinecraftServerRes.SeasonRes toRes(MinecraftServerDTO.SeasonDTO dto) {
        return new MinecraftServerRes.SeasonRes(dto.id(), dto.name(), dto.description(), dto.startedAt(), dto.endedAt(),
                dto.current(), dto.sort());
    }

    private MinecraftEndpointStatusRes toRes(MinecraftEndpointStatusDTO dto) {
        return new MinecraftEndpointStatusRes(dto.endpointId(), dto.status(), dto.onlinePlayers(), dto.maxPlayers(),
                dto.versionName(), dto.protocolId(), dto.ping(), dto.motd(), dto.errorMessage(), dto.checkedAt());
    }

    private MinecraftInheritanceRuleRes toRes(MinecraftInheritanceRuleDTO dto) {
        return new MinecraftInheritanceRuleRes(dto.assetPattern(), dto.minAmount(), dto.maxAmount(), dto.inheritRate(), dto.rangeLabel());
    }

    private MinecraftSeasonAdjustmentRes toRes(MinecraftSeasonAdjustmentDTO dto) {
        return new MinecraftSeasonAdjustmentRes(dto.userId(), dto.assetCode(), dto.inheritedAmount(), dto.seasonIncomeAmount(),
                dto.seasonTotalAmount(), dto.realTotalIncomeAmount(), dto.nextInheritedAmount(), dto.walletBalanceBefore(),
                dto.deltaAmount(), dto.direction(), dto.ruleLabel(), dto.walletTransactionId(), dto.rollbackTransactionId());
    }

    private String textOr(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
