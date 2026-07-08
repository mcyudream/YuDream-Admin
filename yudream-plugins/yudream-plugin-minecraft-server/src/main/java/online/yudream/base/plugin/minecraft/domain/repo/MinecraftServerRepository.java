package online.yudream.base.plugin.minecraft.domain.repo;

import online.yudream.base.plugin.minecraft.domain.aggregate.MinecraftServer;
import online.yudream.base.plugin.minecraft.domain.aggregate.MinecraftSeasonOperation;
import online.yudream.base.plugin.minecraft.domain.aggregate.MinecraftPlayerActivity;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftServerStatus;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftStatusSnapshot;

import java.util.List;
import java.util.Optional;

public interface MinecraftServerRepository {

    MinecraftServer save(MinecraftServer server);

    Optional<MinecraftServer> findById(String id);

    List<MinecraftServer> list(int page, int size, boolean includeDisabled);

    long count(boolean includeDisabled);

    void delete(String id);

    MinecraftServerStatus saveStatus(MinecraftServerStatus status);

    Optional<MinecraftServerStatus> findStatus(String serverId);

    MinecraftStatusSnapshot saveStatusSnapshot(MinecraftStatusSnapshot snapshot);

    List<MinecraftStatusSnapshot> listStatusSnapshots(String serverId, long since, int limit);

    MinecraftSeasonOperation saveOperation(MinecraftSeasonOperation operation);

    Optional<MinecraftSeasonOperation> findOperation(String operationId);

    List<MinecraftSeasonOperation> listOperations(String serverId, int page, int size);

    MinecraftPlayerActivity savePlayerActivity(MinecraftPlayerActivity activity);

    Optional<MinecraftPlayerActivity> findPlayerActivity(String serverId, String playerId);

    List<MinecraftPlayerActivity> listPlayerActivities(String serverId, int page, int size);
}
