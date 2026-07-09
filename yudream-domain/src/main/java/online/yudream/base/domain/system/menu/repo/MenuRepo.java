package online.yudream.base.domain.system.menu.repo;

import online.yudream.base.domain.system.menu.aggregate.Menu;

import java.util.List;
import java.util.Optional;

/**
 * 菜单仓库。
 */
public interface MenuRepo {

    Menu save(Menu menu);

    Optional<Menu> findByCode(String code);

    Optional<Menu> findByPluginCodeAndRegistrationKey(String pluginCode, String registrationKey);

    List<Menu> findAll();

    List<Menu> findByPluginCode(String pluginCode);

    List<Menu> findByTypeIn(List<online.yudream.base.domain.system.menu.enumerate.MenuNodeType> types);

    boolean existsByCode(String code);

    long count();
}
