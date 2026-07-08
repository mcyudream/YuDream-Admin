package online.yudream.base.plugin.activityproof.domain.repo;

import online.yudream.base.plugin.activityproof.domain.aggregate.ActivityProofExportRecord;
import online.yudream.base.plugin.activityproof.domain.aggregate.ActivityProofSettings;
import online.yudream.base.plugin.activityproof.domain.aggregate.PlayerStudentMapping;

import java.util.List;
import java.util.Optional;

public interface ActivityProofRepository {

    ActivityProofSettings settings();

    ActivityProofSettings saveSettings(ActivityProofSettings settings);

    Optional<PlayerStudentMapping> mapping(String serverId, String playerId);

    List<PlayerStudentMapping> mappings(String serverId, int page, int size);

    PlayerStudentMapping saveMapping(PlayerStudentMapping mapping);

    void deleteMapping(String id);

    ActivityProofExportRecord saveExportRecord(ActivityProofExportRecord record);

    Optional<ActivityProofExportRecord> exportRecord(String id);

    List<ActivityProofExportRecord> exportRecords(int page, int size);
}
