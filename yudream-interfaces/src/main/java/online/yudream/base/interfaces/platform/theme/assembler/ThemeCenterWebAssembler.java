package online.yudream.base.interfaces.platform.theme.assembler;

import online.yudream.base.application.platform.theme.cmd.ThemeConfigSaveCmd;
import online.yudream.base.application.platform.theme.dto.ThemeCenterOverviewDTO;
import online.yudream.base.application.platform.theme.dto.ThemeCenterThemeCardDTO;
import online.yudream.base.application.platform.theme.dto.ThemeConfigDTO;
import online.yudream.base.application.platform.theme.dto.ThemeConfigFieldDTO;
import online.yudream.base.application.platform.theme.dto.ThemeConfigOptionDTO;
import online.yudream.base.application.platform.theme.dto.ThemeConfigSchemaDTO;
import online.yudream.base.application.platform.theme.dto.ThemeConfigSectionDTO;
import online.yudream.base.interfaces.platform.cms.assembler.CmsWebAssembler;
import online.yudream.base.interfaces.platform.theme.request.ThemeConfigSaveRequest;
import online.yudream.base.interfaces.platform.theme.res.ThemeCenterOverviewRes;
import online.yudream.base.interfaces.platform.theme.res.ThemeCenterThemeRes;
import online.yudream.base.interfaces.platform.theme.res.ThemeConfigFieldRes;
import online.yudream.base.interfaces.platform.theme.res.ThemeConfigOptionRes;
import online.yudream.base.interfaces.platform.theme.res.ThemeConfigRes;
import online.yudream.base.interfaces.platform.theme.res.ThemeConfigSchemaRes;
import online.yudream.base.interfaces.platform.theme.res.ThemeConfigSectionRes;

import java.util.List;

public class ThemeCenterWebAssembler {

    public static ThemeCenterThemeRes toRes(ThemeCenterThemeCardDTO dto) {
        return ThemeCenterThemeRes.builder()
                .pluginCode(dto.getPluginCode())
                .code(dto.getCode())
                .name(dto.getName())
                .description(dto.getDescription())
                .preview(dto.getPreview())
                .assetRevision(dto.getAssetRevision())
                .hasHomePreset(dto.getHasHomePreset())
                .hasConfigSchema(dto.getHasConfigSchema())
                .hasPageSet(dto.getHasPageSet())
                .active(dto.getActive())
                .enabled(dto.getEnabled())
                .build();
    }

    public static ThemeCenterOverviewRes toRes(ThemeCenterOverviewDTO dto) {
        List<ThemeCenterThemeRes> themes = dto.getThemes().stream()
                .map(ThemeCenterWebAssembler::toRes)
                .toList();
        return ThemeCenterOverviewRes.builder()
                .themes(themes)
                .presets(CmsWebAssembler.toPresetResList(dto.getPresets()))
                .editableThemes(dto.getEditableThemes())
                .cmsEnabled(dto.getCmsEnabled())
                .build();
    }

    public static ThemeConfigSaveCmd toSaveCmd(String theme, ThemeConfigSaveRequest request) {
        ThemeConfigSaveCmd cmd = new ThemeConfigSaveCmd();
        cmd.setThemeCode(theme);
        cmd.setValues(request == null ? null : request.getValues());
        return cmd;
    }

    public static ThemeConfigRes toRes(ThemeConfigDTO dto) {
        return ThemeConfigRes.builder()
                .themeCode(dto.getThemeCode())
                .schema(toRes(dto.getSchema()))
                .values(dto.getValues())
                .secretConfigured(dto.getSecretConfigured())
                .build();
    }

    private static ThemeConfigSchemaRes toRes(ThemeConfigSchemaDTO schema) {
        if (schema == null) {
            return null;
        }
        List<ThemeConfigSectionRes> sections = (schema.getSections() == null ? List.<ThemeConfigSectionDTO>of() : schema.getSections())
                .stream()
                .map(ThemeCenterWebAssembler::toRes)
                .toList();
        return ThemeConfigSchemaRes.builder().sections(sections).build();
    }

    private static ThemeConfigSectionRes toRes(ThemeConfigSectionDTO section) {
        List<ThemeConfigFieldRes> fields = (section.getFields() == null ? List.<ThemeConfigFieldDTO>of() : section.getFields())
                .stream()
                .map(ThemeCenterWebAssembler::toRes)
                .toList();
        return ThemeConfigSectionRes.builder()
                .code(section.getCode())
                .title(section.getTitle())
                .description(section.getDescription())
                .fields(fields)
                .build();
    }

    private static ThemeConfigFieldRes toRes(ThemeConfigFieldDTO field) {
        List<ThemeConfigOptionRes> options = (field.getOptions() == null ? List.<ThemeConfigOptionDTO>of() : field.getOptions())
                .stream()
                .map(option -> ThemeConfigOptionRes.builder().label(option.getLabel()).value(option.getValue()).build())
                .toList();
        List<ThemeConfigFieldRes> itemFields = (field.getItemFields() == null ? List.<ThemeConfigFieldDTO>of() : field.getItemFields())
                .stream()
                .map(ThemeCenterWebAssembler::toRes)
                .toList();
        return ThemeConfigFieldRes.builder()
                .key(field.getKey())
                .label(field.getLabel())
                .description(field.getDescription())
                .type(field.getType())
                .placeholder(field.getPlaceholder())
                .defaultValue(field.getDefaultValue())
                .options(options)
                .secret(field.getSecret())
                .itemFields(itemFields)
                .build();
    }
}
