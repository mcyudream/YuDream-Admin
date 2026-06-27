package online.yudream.base.application.system.user.assembler;

import online.yudream.base.application.system.user.dto.UserDTO;
import online.yudream.base.domain.system.user.aggregate.User;

public class UserAssembler {

    public static UserDTO toDTO(User user){
        return UserDTO.builder()
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail().getValue())
                .phone(user.getPhone().getValue())
                .qq(user.getQq().getValue())
                .build();
    }
}
