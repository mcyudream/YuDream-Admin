package online.yudream.base.application.system.user.assembler;

import online.yudream.base.application.system.user.dto.UserDTO;
import online.yudream.base.application.system.user.dto.UserRegisterDTO;
import online.yudream.base.domain.system.user.aggregate.User;

public class UserAssembler {

    public static UserDTO toDTO(User user){
        return UserDTO.builder()
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail() == null ? null : user.getEmail().getValue())
                .phone(user.getPhone() == null ? null : user.getPhone().getValue())
                .qq(user.getQq() == null ? null : user.getQq().getValue())
                .build();
    }

    public static UserRegisterDTO toRegisterDTO(User user) {
        return UserRegisterDTO.builder()
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail() == null ? null : user.getEmail().getValue())
                .emailVerified(user.isEmailVerified())
                .build();
    }
}
