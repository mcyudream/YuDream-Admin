package online.yudream.base.domain.platform.ai.valobj;

import java.util.Map;

public record AiAgentToolDescriptor(
        String name,
        String title,
        String description,
        String permissionCode,
        String permissionName,
        String permissionModule,
        String permissionDesc,
        Map<String, Object> inputSchema,
        Map<String, Object> inputSchemaDefinition
) {
    public AiAgentToolDescriptor(
            String name,
            String title,
            String description,
            String permissionCode,
            String permissionName,
            String permissionModule,
            String permissionDesc,
            Map<String, Object> inputSchema
    ) {
        this(
                name,
                title,
                description,
                permissionCode,
                permissionName,
                permissionModule,
                permissionDesc,
                inputSchema,
                null
        );
    }
}
