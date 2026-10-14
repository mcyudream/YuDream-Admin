package online.yudream.base.application.platform.chat.cmd;

import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import java.time.LocalDate;

public record ChatQuotaReconcileCmd(String userId, LocalDate usageDate, String reservationId, AiUsage usage) { }
