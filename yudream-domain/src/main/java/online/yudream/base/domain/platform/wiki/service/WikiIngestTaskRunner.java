package online.yudream.base.domain.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.aggregate.WikiIngestTask;
import online.yudream.base.domain.platform.wiki.valobj.WikiIngestProgress;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 摄入任务执行器端口：由持久化队列执行器调用，执行具体业务（两步摄入、级联删除、深度研究等）。
 */
public interface WikiIngestTaskRunner {

    void run(WikiIngestTask task, Consumer<WikiIngestProgress> progress, BooleanSupplier isCancelled);
}
