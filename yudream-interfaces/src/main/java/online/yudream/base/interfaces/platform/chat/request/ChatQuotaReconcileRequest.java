package online.yudream.base.interfaces.platform.chat.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record ChatQuotaReconcileRequest(@NotBlank @Pattern(regexp = "[0-9]{1,19}") String userId,
                                        @NotNull LocalDate usageDate, @NotBlank String reservationId,
                                        @Min(0) long promptTokens, @Min(0) long completionTokens, @Min(0) long totalTokens) { }
