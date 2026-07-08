package online.yudream.base.plugin.activityproof.interfaces.assembler;

import online.yudream.base.plugin.activityproof.application.cmd.ActivityProofExportCmd;
import online.yudream.base.plugin.activityproof.application.cmd.ActivityProofMappingSaveCmd;
import online.yudream.base.plugin.activityproof.application.cmd.ActivityProofSettingsSaveCmd;
import online.yudream.base.plugin.activityproof.application.cmd.ActivityProofTemplateUploadCmd;
import online.yudream.base.plugin.activityproof.interfaces.request.ActivityProofExportRequest;
import online.yudream.base.plugin.activityproof.interfaces.request.ActivityProofMappingSaveRequest;
import online.yudream.base.plugin.activityproof.interfaces.request.ActivityProofSettingsSaveRequest;
import online.yudream.base.plugin.activityproof.interfaces.request.ActivityProofTemplateUploadRequest;

public class ActivityProofWebAssembler {

    public ActivityProofTemplateUploadCmd toCmd(ActivityProofTemplateUploadRequest request) {
        return new ActivityProofTemplateUploadCmd(request.filename(), request.contentType(), request.contentBase64());
    }

    public ActivityProofSettingsSaveCmd toCmd(ActivityProofSettingsSaveRequest request) {
        return new ActivityProofSettingsSaveCmd(request.defaultActivityName(), request.defaultCollege(), request.defaultIssuer());
    }

    public ActivityProofMappingSaveCmd toCmd(ActivityProofMappingSaveRequest request) {
        return new ActivityProofMappingSaveCmd(request.serverId(), request.playerId(), request.playerName(), request.studentNo());
    }

    public ActivityProofExportCmd toCmd(ActivityProofExportRequest request) {
        return new ActivityProofExportCmd(
                request.serverId(),
                request.activityName(),
                request.activityDate(),
                request.proofNo(),
                request.college(),
                request.issuer(),
                request.issueDate(),
                request.minOnlineMinutes(),
                request.includeAfk(),
                request.selectedPlayerIds()
        );
    }
}
