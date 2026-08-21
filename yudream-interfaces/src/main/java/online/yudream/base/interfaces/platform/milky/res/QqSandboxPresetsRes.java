package online.yudream.base.interfaces.platform.milky.res;

import java.util.List;

public record QqSandboxPresetsRes(List<QqSandboxPresetRes> presets,
                                  List<QqSandboxConnectionOptionRes> connections,
                                  List<QqSandboxSenderOptionRes> senders,
                                  List<QqSandboxRoleOptionRes> roles) { }
