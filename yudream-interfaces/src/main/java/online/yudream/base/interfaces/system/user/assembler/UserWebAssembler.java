package online.yudream.base.interfaces.system.user.assembler;

import lombok.NoArgsConstructor;
import online.yudream.base.application.system.user.cmd.UserLoginCmd;
import online.yudream.base.application.system.user.dto.UserDTO;
import online.yudream.base.interfaces.system.user.request.UserLoginRequest;
import online.yudream.base.interfaces.system.user.res.UserRes;

@NoArgsConstructor
public class UserWebAssembler {
    public static UserLoginCmd toLoginCmd(UserLoginRequest request) {
        return UserLoginCmd.builder()
                .username(request.getUsername())
                .password(request.getPassword())
                .build();
    }

    public static UserRes toUserRes(UserDTO userDTO) {
        return UserRes.builder()
                .username(userDTO.getUsername())
                .nickname(userDTO.getNickname())
                .email(userDTO.getEmail())
                .qq(userDTO.getQq())
                .phone(userDTO.getPhone())
                .createTime(userDTO.getCreateTime())
                .build();
    }
}
