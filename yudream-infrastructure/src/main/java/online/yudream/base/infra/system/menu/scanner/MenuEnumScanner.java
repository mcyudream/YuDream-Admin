package online.yudream.base.infra.system.menu.scanner;

import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.anno.MenuModule;
import online.yudream.base.domain.system.menu.anno.MenuNode;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 菜单枚举扫描器。
 * <p>
 * 解析标注了 {@link MenuModule} 的枚举类，将其常量上的 {@link MenuNode} 转换为菜单领域对象树。
 */
@Slf4j
public class MenuEnumScanner {

    /**
     * 扫描多个模块枚举类，返回模块根节点列表。
     */
    public static List<Menu> scan(List<Class<? extends Enum<?>>> moduleClasses) {
        List<Menu> modules = new ArrayList<>();
        for (Class<? extends Enum<?>> clazz : moduleClasses) {
            Menu module = scanModule(clazz);
            if (module != null) {
                modules.add(module);
            }
        }
        return modules;
    }

    private static Menu scanModule(Class<? extends Enum<?>> clazz) {
        MenuModule moduleAnno = clazz.getAnnotation(MenuModule.class);
        if (moduleAnno == null) {
            log.warn("Class {} has no @MenuModule, skip", clazz.getName());
            return null;
        }

        Menu moduleRoot = Menu.builder()
                .code(moduleAnno.code())
                .name(moduleAnno.name())
                .type(MenuNodeType.CATEGORY)
                .module(moduleAnno.code())
                .icon(moduleAnno.icon())
                .sort(moduleAnno.sort())
                .status(MenuStatus.ACTIVE)
                .children(new ArrayList<>())
                .build();

        Map<String, Menu> nodeMap = new LinkedHashMap<>();
        nodeMap.put(moduleRoot.getCode(), moduleRoot);

        Enum<?>[] constants = clazz.getEnumConstants();
        if (constants == null) {
            return moduleRoot;
        }

        // 第一遍：创建所有节点
        for (Enum<?> constant : constants) {
            MenuNode nodeAnno = getMenuNode(constant);
            if (nodeAnno == null) {
                continue;
            }
            Menu menu = buildMenu(constant, nodeAnno, moduleAnno.code());
            nodeMap.put(menu.getCode(), menu);
        }

        // 第二遍：挂载父子关系
        for (Enum<?> constant : constants) {
            MenuNode nodeAnno = getMenuNode(constant);
            if (nodeAnno == null) {
                continue;
            }
            Menu menu = nodeMap.get(nodeAnno.code());
            if (menu == null) {
                continue;
            }
            String parentCode = resolveParentCode(constant, nodeAnno, nodeMap);
            menu.setParentCode(parentCode);
            Menu parent = nodeMap.get(parentCode);
            if (parent != null) {
                parent.getChildren().add(menu);
            } else {
                // 如果找不到父节点，挂到模块根下
                moduleRoot.getChildren().add(menu);
                menu.setParentCode(moduleRoot.getCode());
            }
        }

        return moduleRoot;
    }

    private static MenuNode getMenuNode(Enum<?> constant) {
        try {
            Field field = constant.getDeclaringClass().getField(constant.name());
            return field.getAnnotation(MenuNode.class);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static Menu buildMenu(Enum<?> constant, MenuNode nodeAnno, String moduleCode) {
        String module = StringUtils.hasText(nodeAnno.module()) ? nodeAnno.module() : moduleCode;
        return Menu.builder()
                .code(nodeAnno.code())
                .name(nodeAnno.name())
                .type(nodeAnno.type())
                .module(module)
                .icon(nodeAnno.icon())
                .path(nodeAnno.path())
                .component(nodeAnno.component())
                .link(nodeAnno.link())
                .sort(nodeAnno.sort())
                .permission(StringUtils.hasText(nodeAnno.permission()) ? nodeAnno.permission() : nodeAnno.code())
                .status(MenuStatus.ACTIVE)
                .children(new ArrayList<>())
                .build();
    }

    private static String resolveParentCode(Enum<?> constant, MenuNode nodeAnno, Map<String, Menu> nodeMap) {
        // 优先通过 parentClass + parentName 引用枚举常量
        Class<?> parentClass = nodeAnno.parentClass();
        String parentName = nodeAnno.parentName();
        if (StringUtils.hasText(parentName)) {
            if (parentClass == null || Object.class.equals(parentClass)) {
                parentClass = constant.getDeclaringClass();
            }
            if (!parentClass.isEnum()) {
                log.warn("Parent class {} is not an enum, fallback to parentCode", parentClass.getName());
            } else {
                try {
                    Enum<?> parentConstant = Enum.valueOf((Class<? extends Enum>) parentClass, parentName);
                    MenuNode parentNode = getMenuNode(parentConstant);
                    if (parentNode != null) {
                        return parentNode.code();
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Parent enum constant {} not found in {}, fallback to parentCode",
                            parentName, parentClass.getName());
                }
            }
        }
        if (StringUtils.hasText(nodeAnno.parentCode())) {
            return nodeAnno.parentCode();
        }
        return null;
    }
}
