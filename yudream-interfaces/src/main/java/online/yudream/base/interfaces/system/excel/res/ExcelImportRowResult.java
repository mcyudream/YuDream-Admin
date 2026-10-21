package online.yudream.base.interfaces.system.excel.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单行 Excel 导入的可审计结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelImportRowResult {

    public enum Action {
        CREATED,
        UPDATED,
        SKIPPED
    }

    @Builder.Default
    private Action action = Action.UPDATED;

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    public static ExcelImportRowResult created() {
        return builder().action(Action.CREATED).build();
    }

    public static ExcelImportRowResult created(String warning) {
        return builder().action(Action.CREATED)
                .warnings(warning == null || warning.isBlank() ? List.of() : List.of(warning))
                .build();
    }

    public static ExcelImportRowResult updated() {
        return builder().action(Action.UPDATED).build();
    }

    public static ExcelImportRowResult skipped(String warning) {
        return builder().action(Action.SKIPPED)
                .warnings(warning == null || warning.isBlank() ? List.of() : List.of(warning))
                .build();
    }
}
