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

    /** 按名称与父部门精确查找（parentId 为 null 表示顶层）；模板导入按路径解析目标部门时使用。 */
    Optional<Dept> findByNameAndParent(String name, Long parentId);

    long countActiveChildren(Long parentId);

    void deleteById(Long id);
}
