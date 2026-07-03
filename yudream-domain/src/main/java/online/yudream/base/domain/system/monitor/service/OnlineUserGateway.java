package online.yudream.base.domain.system.monitor.service;

import online.yudream.base.domain.system.monitor.dto.OnlineUserDTO;

import java.util.List;

public interface OnlineUserGateway {

    List<OnlineUserDTO> list(String keyword, int limit);

    void kickout(String token);
}
