package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.dto.PluginDeptCatalogDTO;
import online.yudream.base.application.platform.plugin.dto.PluginRoleCatalogDTO;
import online.yudream.base.application.platform.plugin.dto.PluginUserCatalogDTO;
import online.yudream.base.application.system.user.dto.OptionDTO;
import online.yudream.base.application.system.user.service.RoleManageAppService;
import online.yudream.base.plugin.spi.system.user.PluginDeptOption;
import online.yudream.base.plugin.spi.system.user.PluginUserOption;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;
import online.yudream.base.plugin.spi.system.user.PluginUserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PluginUserCatalogAppService {

    private final PluginUserService pluginUserService;
    private final RoleManageAppService roleManageAppService;

    public List<PluginUserCatalogDTO> searchUsers(String keyword, String deptId, int page, int size) {
        Long parsedDeptId = parseId(deptId);
        return pluginUserService.searchUsers(trimToNull(keyword), parsedDeptId, Math.max(page, 1), clampSize(size, 20, 200))
                .stream()
                .map(PluginUserCatalogAppService::toUser)
                .toList();
    }

    public List<PluginUserCatalogDTO> resolveUsers(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<PluginUserCatalogDTO> result = new ArrayList<>();
        for (String id : ids) {
            if (!StringUtils.hasText(id)) {
                continue;
            }
            PluginUserCatalogDTO user = resolveUser(id.trim());
            if (user != null) {
                result.add(user);
            }
        }
        return List.copyOf(result);
    }

    public List<PluginDeptCatalogDTO> departments(String keyword) {
        return pluginUserService.listDepartments(trimToNull(keyword)).stream()
                .map(node -> toDept(node, ""))
                .toList();
    }

    public List<PluginDeptCatalogDTO> flattenDepartments(String keyword) {
        List<PluginDeptCatalogDTO> result = new ArrayList<>();
        for (PluginDeptOption root : pluginUserService.listDepartments(trimToNull(keyword))) {
            flatten(root, "", result);
        }
        return List.copyOf(result);
    }

    public List<PluginRoleCatalogDTO> roles() {
        return roleManageAppService.options().stream()
                .map(PluginUserCatalogAppService::toRole)
                .toList();
    }

    private PluginUserCatalogDTO resolveUser(String id) {
        Long numericId = parseId(id);
        if (numericId != null) {
            return pluginUserService.findById(numericId)
                    .filter(profile -> id.equals(String.valueOf(profile.id())))
                    .map(PluginUserCatalogAppService::toUser)
                    .orElse(null);
        }
        return pluginUserService.searchUsers(id, null, 1, 1).stream()
                .filter(user -> id.equals(user.id()))
                .findFirst()
                .map(PluginUserCatalogAppService::toUser)
                .orElse(null);
    }

    private static PluginUserCatalogDTO toUser(PluginUserProfile user) {
        return PluginUserCatalogDTO.builder()
                .id(user.id() == null ? null : String.valueOf(user.id()))
                .username(user.username())
                .nickname(user.nickname())
                .email(user.email())
                .avatar(user.avatar())
                .status(user.status())
                .deptIds(List.of())
                .deptNames(List.of())
                .build();
    }

    private static PluginUserCatalogDTO toUser(PluginUserOption user) {
        return PluginUserCatalogDTO.builder()
                .id(user.id())
                .username(user.username())
                .nickname(user.nickname())
                .email(user.email())
                .avatar(user.avatar())
                .status(user.status())
                .deptIds(user.deptIds() == null ? List.of() : user.deptIds())
                .deptNames(user.deptNames() == null ? List.of() : user.deptNames())
                .build();
    }

    private static PluginDeptCatalogDTO toDept(PluginDeptOption node, String parentLabel) {
        String name = text(node.name(), node.id());
        String label = parentLabel.isBlank() ? name : parentLabel + " / " + name;
        return PluginDeptCatalogDTO.builder()
                .id(node.id())
                .name(name)
                .label(label)
                .parentId(node.parentId())
                .status(node.status())
                .children(node.children() == null ? List.of() : node.children().stream()
                        .map(child -> toDept(child, label))
                        .toList())
                .build();
    }

    private static void flatten(PluginDeptOption node, String parentLabel, List<PluginDeptCatalogDTO> output) {
        if (node == null || !StringUtils.hasText(node.id())) {
            return;
        }
        String name = text(node.name(), node.id());
        String label = parentLabel.isBlank() ? name : parentLabel + " / " + name;
        output.add(PluginDeptCatalogDTO.builder()
                .id(node.id())
                .name(name)
                .label(label)
                .parentId(node.parentId())
                .status(node.status())
                .children(List.of())
                .build());
        for (PluginDeptOption child : node.children() == null ? List.<PluginDeptOption>of() : node.children()) {
            flatten(child, label, output);
        }
    }

    private static PluginRoleCatalogDTO toRole(OptionDTO option) {
        return PluginRoleCatalogDTO.builder()
                .id(option.getId() == null ? option.getValue() : String.valueOf(option.getId()))
                .code(option.getValue())
                .name(option.getLabel())
                .deptId(option.getDeptId() == null ? null : String.valueOf(option.getDeptId()))
                .deptName(option.getDeptName())
                .build();
    }

    private static int clampSize(int size, int fallback, int max) {
        int value = size <= 0 ? fallback : size;
        return Math.min(Math.max(value, 1), max);
    }

    private static Long parseId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String text(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
