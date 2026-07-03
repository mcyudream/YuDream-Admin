package online.yudream.base.interfaces.system.user.assembler;

import online.yudream.base.application.system.security.cmd.LoginTokenRefreshCmd;
import online.yudream.base.application.system.security.dto.LoginTokenDTO;
import lombok.NoArgsConstructor;
import online.yudream.base.application.system.user.cmd.UserLoginCmd;
import online.yudream.base.application.system.user.cmd.UserProfileUpdateCmd;
import online.yudream.base.application.system.user.cmd.UserRegisterCmd;
import online.yudream.base.application.system.user.dto.UserContextVO;
import online.yudream.base.application.system.user.dto.UserDTO;
import online.yudream.base.application.system.user.dto.UserDeptVO;
import online.yudream.base.application.system.user.dto.UserLoginDTO;
import online.yudream.base.application.system.user.dto.UserProfileDTO;
import online.yudream.base.application.system.user.dto.UserRegisterDTO;
import online.yudream.base.application.system.user.dto.UserRoleVO;
import online.yudream.base.domain.system.monitor.dto.LoginLogDTO;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.interfaces.system.user.request.UserLoginRequest;
import online.yudream.base.interfaces.system.user.request.UserProfileUpdateRequest;
import online.yudream.base.interfaces.system.user.request.UserRegisterRequest;
import online.yudream.base.interfaces.system.user.request.UserTokenRefreshRequest;
import online.yudream.base.interfaces.system.user.res.UserLoginRes;
import online.yudream.base.interfaces.system.user.res.UserContextRes;
import online.yudream.base.interfaces.system.user.res.UserDeptRes;
import online.yudream.base.interfaces.system.user.res.UserProfileRes;
import online.yudream.base.interfaces.system.user.res.UserRegisterRes;
import online.yudream.base.interfaces.system.user.res.UserRoleRes;
import online.yudream.base.interfaces.system.user.res.UserRes;
import online.yudream.base.interfaces.system.user.vo.PermissionListVO;

import java.util.List;

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

    public static LoginTokenRefreshCmd toCmd(UserTokenRefreshRequest request) {
        LoginTokenRefreshCmd cmd = new LoginTokenRefreshCmd();
        cmd.setRefreshToken(request.getRefreshToken());
        return cmd;
    }

    public static UserProfileUpdateCmd toProfileUpdateCmd(UserProfileUpdateRequest request) {
        UserProfileUpdateCmd cmd = new UserProfileUpdateCmd();
        cmd.setNickname(request.getNickname());
        cmd.setEmail(request.getEmail());
        cmd.setPhone(request.getPhone());
        cmd.setQq(request.getQq());
        return cmd;
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

    public static UserProfileRes toProfileRes(UserProfileDTO dto) {
        return UserProfileRes.builder()
                .id(dto.getId())
                .username(dto.getUsername())
                .nickname(dto.getNickname())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .qq(dto.getQq())
                .avatar(dto.getAvatar())
                .avatarFileId(dto.getAvatarFileId())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static List<UserDeptRes> toDeptResList(List<UserDeptVO> items) {
        return items == null ? List.of() : items.stream().map(UserWebAssembler::toRes).toList();
    }

    public static List<UserRoleRes> toRoleResList(List<UserRoleVO> items) {
        return items == null ? List.of() : items.stream().map(UserWebAssembler::toRes).toList();
    }

    public static UserContextRes toContextRes(UserContextVO dto) {
        return UserContextRes.builder()
                .currentDept(dto.getCurrentDept() == null ? null : toRes(dto.getCurrentDept()))
                .currentRole(dto.getCurrentRole() == null ? null : toRes(dto.getCurrentRole()))
                .build();
    }

    public static UserDeptRes toRes(UserDeptVO dto) {
        return UserDeptRes.builder()
                .id(dto.getId())
                .name(dto.getName())
                .current(dto.isCurrent())
                .defaultDept(dto.isDefaultDept())
                .build();
    }

    public static UserRoleRes toRes(UserRoleVO dto) {
        return UserRoleRes.builder()
                .id(dto.getId())
                .name(dto.getName())
                .code(dto.getCode())
                .current(dto.isCurrent())
                .build();
    }

    public static UserLoginRes toLoginRes(UserLoginDTO dto) {
        return UserLoginRes.builder()
                .token(dto.getToken())
                .tokenName(dto.getTokenName())
                .refreshToken(dto.getRefreshToken())
                .dualTokenEnabled(dto.isDualTokenEnabled())
                .expiresIn(dto.getExpiresIn())
                .userId(dto.getUserId())
                .username(dto.getUsername())
                .nickname(dto.getNickname())
                .email(dto.getEmail())
                .avatar(dto.getAvatar())
                .createTime(dto.getCreateTime())
                .build();
    }

    public static UserLoginRes toLoginRes(User user, LoginTokenDTO token, String avatar) {
        return UserLoginRes.builder()
                .token(token.getToken())
                .tokenName(token.getTokenName())
                .refreshToken(token.getRefreshToken())
                .dualTokenEnabled(token.isDualTokenEnabled())
                .expiresIn(token.getExpiresIn())
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail() == null ? null : user.getEmail().getValue())
                .avatar(avatar)
                .createTime(user.getCreateTime())
                .build();
    }

    public static UserLoginRes toLoginRes(LoginTokenDTO token) {
        return UserLoginRes.builder()
                .token(token.getToken())
                .tokenName(token.getTokenName())
                .refreshToken(token.getRefreshToken())
                .dualTokenEnabled(token.isDualTokenEnabled())
                .expiresIn(token.getExpiresIn())
                .build();
    }

    public static LoginLogDTO toLoginLogDTO(UserLoginRequest request, User user, boolean success, String message, String ip, String userAgent, String token) {
        return LoginLogDTO.builder()
                .username(request.getUsername())
                .userId(user == null ? null : user.getId())
                .success(success)
                .message(message)
                .ip(ip)
                .userAgent(userAgent)
                .token(token)
                .build();
    }

    public static PermissionListVO toPermissionListVO(List<String> permissions) {
        return PermissionListVO.builder().permissions(permissions).build();
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
