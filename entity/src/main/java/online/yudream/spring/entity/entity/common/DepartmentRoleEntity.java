package online.yudream.spring.entity.entity.common;

import lombok.*;
import online.yudream.spring.base.enums.DataRange;
import online.yudream.spring.entity.entity.Department;
import online.yudream.spring.entity.entity.Role;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class DepartmentRoleEntity {
    @DocumentReference
    private Department department;
    @DocumentReference
    private Role role;
    private DataRange dataRange=DataRange.SELF;
}