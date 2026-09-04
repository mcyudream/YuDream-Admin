package online.yudream.base.application.platform.form.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicFormSchemaSupportTest {

    @Test
    void collectsUploadFieldsIncludingNestedChildren() {
        String schema = """
                [
                  {"type": "input", "field": "name", "title": "姓名"},
                  {"type": "upload", "field": "attachment", "title": "附件"},
                  {"type": "group", "field": "items", "children": [
                    {"type": "FcUpload", "field": "innerFile", "title": "内部文件"}
                  ]}
                ]
                """;
        Set<String> fields = DynamicFormSchemaSupport.uploadFields(schema);
        assertEquals(Set.of("attachment", "innerFile"), fields);
    }

    @Test
    void returnsEmptyOnBlankOrInvalidSchema() {
        assertTrue(DynamicFormSchemaSupport.uploadFields(null).isEmpty());
        assertTrue(DynamicFormSchemaSupport.uploadFields("").isEmpty());
        assertTrue(DynamicFormSchemaSupport.uploadFields("not-json").isEmpty());
    }

    @Test
    void parsesFileIdFromRelativeAndAbsoluteUrl() {
        assertEquals(123L, DynamicFormSchemaSupport.fileId("/api/files/123/content"));
        assertEquals(456L, DynamicFormSchemaSupport.fileId("https://demo.example.com/api/files/456/content"));
        assertNull(DynamicFormSchemaSupport.fileId("/api/other/789"));
        assertNull(DynamicFormSchemaSupport.fileId(null));
    }

    @Test
    void collectsDistinctFileIdsFromListValue() {
        Object value = List.of("/api/files/1/content", "/api/files/2/content", "/api/files/1/content");
        assertEquals(List.of(1L, 2L), DynamicFormSchemaSupport.fileIds(value));
    }

    @Test
    void ignoresNonFileValues() {
        assertTrue(DynamicFormSchemaSupport.fileIds("plain text").isEmpty());
        assertTrue(DynamicFormSchemaSupport.fileIds(Map.of("url", "/api/files/9/content")).isEmpty());
        assertTrue(DynamicFormSchemaSupport.fileIds(null).isEmpty());
    }
}
