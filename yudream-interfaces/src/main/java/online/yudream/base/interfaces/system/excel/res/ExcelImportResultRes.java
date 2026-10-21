package online.yudream.base.interfaces.system.excel.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelImportResultRes {
    private int total;
    private int success;
    private int failed;
    @Builder.Default
    private int created = 0;
    @Builder.Default
    private int updated = 0;
    @Builder.Default
    private int skipped = 0;
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    public void record(ExcelImportRowResult rowResult) {
        ExcelImportRowResult result = rowResult == null ? ExcelImportRowResult.updated() : rowResult;
        switch (result.getAction()) {
            case CREATED -> {
                created++;
                success++;
            }
            case UPDATED -> {
                updated++;
                success++;
            }
            case SKIPPED -> skipped++;
        }
        if (result.getWarnings() != null) {
            warnings.addAll(result.getWarnings());
        }
    }
}
