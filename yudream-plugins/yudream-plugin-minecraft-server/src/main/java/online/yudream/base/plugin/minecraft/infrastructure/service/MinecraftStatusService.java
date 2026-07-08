package online.yudream.base.plugin.minecraft.infrastructure.service;

import net.lenni0451.mcping.MCPing;
import net.lenni0451.mcping.responses.BedrockPingResponse;
import net.lenni0451.mcping.responses.MCPingResponse;
import online.yudream.base.plugin.minecraft.domain.enumerate.MinecraftEdition;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftEndpointStatus;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftServerEndpoint;

public class MinecraftStatusService {

    public MinecraftEndpointStatus ping(MinecraftServerEndpoint endpoint) {
        if (!endpoint.enabled()) {
            return MinecraftEndpointStatus.offline(endpoint.id(), "线路已停用");
        }
        try {
            if (endpoint.edition() == MinecraftEdition.BEDROCK) {
                BedrockPingResponse response = MCPing.pingBedrock()
                        .address(endpoint.host(), endpoint.port())
                        .timeout(3000, 3000)
                        .getSync();
                return MinecraftEndpointStatus.online(endpoint.id(), response.getOnlinePlayers(), response.getMaxPlayers(),
                        response.getVersionName(), response.getProtocolId(), response.getPing(), response.getMotd());
            }
            MCPingResponse response = MCPing.pingModern()
                    .address(endpoint.host(), endpoint.port())
                    .timeout(3000, 3000)
                    .getSync();
            return MinecraftEndpointStatus.online(endpoint.id(), response.getOnlinePlayers(), response.getMaxPlayers(),
                    response.getVersionName(), response.getProtocolId(), response.getPing(), response.getMotd());
        } catch (RuntimeException e) {
            return MinecraftEndpointStatus.offline(endpoint.id(), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
