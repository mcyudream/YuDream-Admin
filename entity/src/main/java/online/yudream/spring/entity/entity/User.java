package online.yudream.spring.entity.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import online.yudream.spring.entity.entity.common.IpEntity;
import online.yudream.spring.entity.enums.UserStatus;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@Document("sys_tb_user")
public class User {
    @Id
    @MongoId(value = FieldType.STRING)
    private String id;
    @JsonIgnore
    private String password;
    private String nickname;
    private String email;
    private String phone;
    private List<IpEntity> ips; // 近n次登录ip

    @DocumentReference(lazy = false)
    private List<Department>  departments;

    private UserStatus status = UserStatus.NORMAL;

    @DocumentReference(lazy = false)
    private List<Role> roles;
    @CreatedDate
    private LocalDateTime createTime;
    @LastModifiedDate
    private LocalDateTime updateTime;

}
