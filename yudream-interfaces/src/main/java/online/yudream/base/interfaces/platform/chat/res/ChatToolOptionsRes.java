package online.yudream.base.interfaces.platform.chat.res;

import java.util.List;

public record ChatToolOptionsRes(List<Module> modules, List<Skill> skills) {
    public record Module(String code, String name, String description, String icon, String source, List<String> tools) {
    }

    public record Skill(String name, String description) {
    }
}
