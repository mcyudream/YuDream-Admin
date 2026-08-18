package online.yudream.base.domain.platform.wiki.aggregate;

import online.yudream.base.domain.platform.wiki.enumerate.WikiExtractionStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestTaskStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestTaskType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiReviewItemType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiReviewStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiKnowledgeAggregatesTest {

    @Test
    void sourceLifecycleTracksExtractionAndIngest() {
        WikiSource source = WikiSource.file(1L, "/papers", "a.pdf", "A", "application/pdf", 100L, "hash-1");
        source.markExtracted("正文", List.of());
        assertEquals(WikiExtractionStatus.EXTRACTED, source.getExtractionStatus());

        source.markIngested("hash-1");
        assertEquals(WikiIngestStatus.INGESTED, source.getIngestStatus());
        assertTrue(source.isUnchangedSinceIngest("hash-1"));
        assertFalse(source.isUnchangedSinceIngest("hash-2"));
    }

    @Test
    void ingestTaskRetriesAndCancels() {
        WikiIngestTask task = WikiIngestTask.create(1L, 2L, WikiIngestTaskType.INGEST, null, 1L);
        task.start();
        task.fail("失败");
        assertEquals(WikiIngestTaskStatus.FAILED, task.getStatus());
        assertTrue(task.canRetry());
        task.requeue();
        assertEquals(WikiIngestTaskStatus.QUEUED, task.getStatus());

        task.start();
        task.cancel();
        assertEquals(WikiIngestTaskStatus.CANCELLED, task.getStatus());
        task.resetForRetry();
        assertEquals(WikiIngestTaskStatus.QUEUED, task.getStatus());
        assertEquals(0, task.getAttempts());
    }

    @Test
    void reviewItemResolvesAndDismisses() {
        WikiReviewItem item = WikiReviewItem.create(1L, 2L, WikiReviewItemType.DEEP_RESEARCH, "研究", "描述",
                "动作", List.of("q1"), List.of("p1"));
        assertEquals(WikiReviewStatus.PENDING, item.getStatus());
        item.resolve();
        assertEquals(WikiReviewStatus.DONE, item.getStatus());

        WikiReviewItem another = WikiReviewItem.create(1L, 2L, WikiReviewItemType.FLAG, "F", "D", "A", List.of(), List.of());
        another.dismiss();
        assertEquals(WikiReviewStatus.DISMISSED, another.getStatus());
    }
}
