package online.yudream.base.domain.system.user.repo;

import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;

import java.util.Optional;

public interface DeptRepo {

    Dept save(Dept dept);

    Optional<Dept> findById(Long id);

    Optional<Dept> findRoot();

    Optional<Dept> findByType(SystemDeptType type);
}
