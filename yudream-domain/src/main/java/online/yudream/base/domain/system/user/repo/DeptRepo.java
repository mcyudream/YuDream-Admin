package online.yudream.base.domain.system.user.repo;

import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;

import java.util.List;
import java.util.Optional;

public interface DeptRepo {

    Dept save(Dept dept);

    Optional<Dept> findById(Long id);

    Optional<Dept> findRoot();

    Optional<Dept> findByType(SystemDeptType type);

    List<Dept> findAll();

    List<Dept> findByIds(List<Long> ids);

    List<Dept> findChildren(Long parentId);

    List<Dept> tree(String keyword, Long parentId, DeptStatus status);

    boolean existsByNameAndParentExcludeId(String name, Long parentId, Long excludeId);

    long countActiveChildren(Long parentId);
}
