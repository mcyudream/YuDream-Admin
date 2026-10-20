package online.yudream.base.infra.platform.cms.impl;

import com.mongodb.client.result.UpdateResult;
import online.yudream.base.domain.platform.cms.aggregate.StudioWorkspace;
import online.yudream.base.infra.platform.cms.dataobj.StudioWorkspaceDO;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudioWorkspaceRepoImplTest {
    final MongoTemplate mongo = mock(MongoTemplate.class);
    final StudioWorkspaceRepoImpl repo = new StudioWorkspaceRepoImpl(mongo);
    @Test void firstWriteUsesUniqueThemeIdAndDuplicateMeansConflictWithoutRetry() {
        var workspace = StudioWorkspace.empty("alpha").saveDraft("0", "{}", Instant.now());
        when(mongo.insert(any(StudioWorkspaceDO.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThat(repo.compareAndSet(0, workspace)).isFalse();
        ArgumentCaptor<StudioWorkspaceDO> data = ArgumentCaptor.forClass(StudioWorkspaceDO.class);
        verify(mongo, times(1)).insert(data.capture());
        assertThat(data.getValue().getThemeCode()).isEqualTo("alpha");
        assertThat(data.getValue().getRevision()).isEqualTo(1);
        verify(mongo, never()).save(any());
        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(StudioWorkspaceDO.class));
    }
    @Test void publishWritesSnapshotAndPointerAndRevisionInExactlyOneConditionalUpdate() {
        var workspace = StudioWorkspace.empty("alpha").saveDraft("0", "{draft}", Instant.now()).publish("1", "release", Instant.now());
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(StudioWorkspaceDO.class))).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        assertThat(repo.compareAndSet(1, workspace)).isTrue();
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(1)).updateFirst(query.capture(), update.capture(), eq(StudioWorkspaceDO.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("_id", "alpha").append("revision", 1L));
        Document set = update.getValue().getUpdateObject().get("$set", Document.class);
        assertThat(set.get("revision")).isEqualTo(2L);
        assertThat(set.get("publishedReleaseId")).isEqualTo("release");
        assertThat(set.get("documentJson")).isEqualTo("{draft}");
        assertThat(set.get("releases", java.util.List.class)).hasSize(1);
        verify(mongo, never()).save(any());
    }
    @Test void zeroMatchedDocumentsIsConflictAndCannotUpsertOrOverwriteOtherThemes() {
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(StudioWorkspaceDO.class))).thenReturn(UpdateResult.acknowledged(0, 0L, null));
        var workspace = StudioWorkspace.empty("alpha").saveDraft("0", "{}", Instant.now());
        assertThat(repo.compareAndSet(8, workspace)).isFalse();
        verify(mongo, times(1)).updateFirst(any(Query.class), any(Update.class), eq(StudioWorkspaceDO.class));
        verifyNoMoreInteractions(mongo);
    }
}
