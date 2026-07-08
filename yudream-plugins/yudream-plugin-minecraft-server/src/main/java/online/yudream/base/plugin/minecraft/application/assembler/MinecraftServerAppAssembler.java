package online.yudream.base.plugin.minecraft.application.assembler;

import online.yudream.base.plugin.minecraft.application.dto.MinecraftEndpointStatusDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftInheritanceRuleDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftSeasonAdjustmentDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftSeasonOperationDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftPlayerActivityDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftServerDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftServerStatusDTO;
import online.yudream.base.plugin.minecraft.domain.aggregate.MinecraftSeasonOperation;
import online.yudream.base.plugin.minecraft.domain.aggregate.MinecraftServer;
import online.yudream.base.plugin.minecraft.domain.aggregate.MinecraftPlayerActivity;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftEndpointStatus;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftInheritanceRule;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftSeasonAdjustment;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftServerEndpoint;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftServerSeason;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftServerStatus;

import java.math.BigDecimal;
import java.util.Map;

public class MinecraftServerAppAssembler {

    public MinecraftServerDTO toDTO(MinecraftServer server, MinecraftServerStatus status) {
        return new MinecraftServerDTO(
                server.id(),
                server.name(),
                server.descriptionMarkdown(),
                server.enabled(),
                server.sort(),
                server.endpoints().stream().map(this::toDTO).toList(),
                server.seasons().stream().map(this::toDTO).toList(),
                server.currentSeason() == null ? null : toDTO(server.currentSeason()),
                status == null ? null : toDTO(status),
                server.createdAt(),
                server.updatedAt()
        );
    }

    public MinecraftServerDTO.EndpointDTO toDTO(MinecraftServerEndpoint endpoint) {
        return new MinecraftServerDTO.EndpointDTO(endpoint.id(), endpoint.name(), endpoint.host(), endpoint.port(),
                endpoint.edition().name(), endpoint.primaryLine(), endpoint.enabled(), endpoint.sort());
    }

    public MinecraftServerDTO.SeasonDTO toDTO(MinecraftServerSeason season) {
        return new MinecraftServerDTO.SeasonDTO(season.id(), season.name(), season.description(), season.startedAt(),
                season.endedAt(), season.current(), season.sort());
    }

    public MinecraftServerStatusDTO toDTO(MinecraftServerStatus status) {
        return new MinecraftServerStatusDTO(status.serverId(), status.status(), status.onlinePlayers(), status.maxPlayers(),
                status.endpoints().stream().map(this::toDTO).toList(), status.checkedAt());
    }

    public MinecraftEndpointStatusDTO toDTO(MinecraftEndpointStatus status) {
        return new MinecraftEndpointStatusDTO(status.endpointId(), status.status(), status.onlinePlayers(), status.maxPlayers(),
                status.versionName(), status.protocolId(), status.ping(), status.motd(), status.errorMessage(), status.checkedAt());
    }

    public MinecraftSeasonOperationDTO toDTO(MinecraftSeasonOperation operation, Map<String, BigDecimal> realTotals) {
        return new MinecraftSeasonOperationDTO(
                operation.id(),
                operation.serverId(),
                operation.fromSeasonId(),
                operation.toSeasonId(),
                operation.toSeasonName(),
                operation.status().name(),
                operation.rules().stream().map(this::toDTO).toList(),
                operation.adjustments().stream()
                        .map(adjustment -> toDTO(adjustment, realTotals == null ? BigDecimal.ZERO : realTotals.getOrDefault(key(adjustment.userId(), adjustment.assetCode()), BigDecimal.ZERO)))
                        .toList(),
                operation.operatorUserId(),
                operation.remark(),
                operation.createdAt(),
                operation.rolledBackAt()
        );
    }

    public MinecraftInheritanceRuleDTO toDTO(MinecraftInheritanceRule rule) {
        return new MinecraftInheritanceRuleDTO(rule.assetPattern(), rule.minAmount(), rule.maxAmount(), rule.inheritRate(), rule.rangeLabel());
    }

    public MinecraftSeasonAdjustmentDTO toDTO(MinecraftSeasonAdjustment adjustment, BigDecimal realTotal) {
        return new MinecraftSeasonAdjustmentDTO(
                adjustment.userId(),
                adjustment.assetCode(),
                adjustment.inheritedAmount(),
                adjustment.seasonIncomeAmount(),
                adjustment.seasonTotalAmount(),
                realTotal,
                adjustment.nextInheritedAmount(),
                adjustment.walletBalanceBefore(),
                adjustment.deltaAmount(),
                adjustment.direction(),
                adjustment.ruleLabel(),
                adjustment.walletTransactionId(),
                adjustment.rollbackTransactionId()
        );
    }

    public MinecraftPlayerActivityDTO toDTO(MinecraftPlayerActivity activity, long now) {
        return new MinecraftPlayerActivityDTO(
                activity.serverId(),
                activity.playerId(),
                activity.playerName(),
                activity.online(),
                activity.afk(),
                activity.totalOnlineMillisAt(now),
                activity.totalAfkMillisAt(now),
                activity.currentOnlineSince(),
                activity.currentAfkSince(),
                activity.lastJoinedAt(),
                activity.lastQuitAt(),
                activity.updatedAt()
        );
    }

    private String key(String userId, String assetCode) {
        return userId + ":" + assetCode;
    }
}
