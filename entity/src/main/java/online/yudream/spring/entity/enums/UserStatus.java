package online.yudream.spring.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum UserStatus {
    DELETE("status.delete",0),
    NORMAL("status.normal",1),
    BANNED("status.banned",2),
    ;
    private final String description;
    private final Integer code;


}
