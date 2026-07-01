package online.yudream.base.interfaces.system.user.assembler;

import lombok.NoArgsConstructor;
import online.yudream.base.application.system.user.cmd.UserLoginCmd;
import online.yudream.base.application.system.user.cmd.UserRegisterCmd;
import online.yudream.base.application.system.user.dto.UserDTO;
import online.yudream.base.application.system.user.dto.UserLoginDTO;
import online.yudream.base.application.system.user.dto.UserRegisterDTO;
import online.yudream.base.interfaces.system.user.request.UserLoginRequest;
import online.yudream.base.interfaces.system.user.request.UserRegisterRequest;
import online.yudream.base.interfaces.system.user.res.UserLoginRes;
import online.yudream.base.interfaces.system.user.res.UserRegisterRes;
import online.yudream.base.interfaces.system.user.res.UserRes;

@NoArgsConstructor
public class UserWebAssembler {
    public static UserLoginCmd toLoginCmd(UserLoginRequest request) {
        return UserLoginCmd.builder()
                .username(request.getUsername())
                .password(request.getPassword())
                .build();
    }

    public static UserRegisterCmd toRegisterCmd(UserRegisterRequest request) {
        return UserRegisterCmd.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(request.getPassword())
                .nickname(request.getNickname())
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

    public static UserLoginRes toLoginRes(UserLoginDTO dto) {
        return UserLoginRes.builder()
                .token(dto.getToken())
                .tokenName(dto.getTokenName())
                .userId(dto.getUserId())
                .username(dto.getUsername())
                .nickname(dto.getNickname())
                .email(dto.getEmail())
                .createTime(dto.getCreateTime())
                .build();
    }

    public static UserRegisterRes toRegisterRes(UserRegisterDTO dto) {
        return UserRegisterRes.builder()
                .username(dto.getUsername())
                .email(dto.getEmail())
                .nickname(dto.getNickname())
                .emailVerified(dto.isEmailVerified())
                .build();
    }
}
