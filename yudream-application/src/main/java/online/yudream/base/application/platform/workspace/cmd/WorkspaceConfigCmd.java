package online.yudream.base.application.platform.workspace.cmd;

import lombok.Data;

@Data
public class WorkspaceConfigCmd {
    private long quotaMb;
    private int tmpRetentionDays;
}
