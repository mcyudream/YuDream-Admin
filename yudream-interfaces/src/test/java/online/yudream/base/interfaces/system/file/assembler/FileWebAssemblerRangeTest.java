package online.yudream.base.interfaces.system.file.assembler;

import online.yudream.base.application.system.file.dto.FileByteRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FileWebAssemblerRangeTest {

    @Test
    void parsesSingleByteRangesAndIgnoresUnsupportedForms() {
        assertEquals(new FileByteRange(0L, 99L), FileWebAssembler.byteRange("bytes=0-99"));
        assertEquals(new FileByteRange(500L, null), FileWebAssembler.byteRange("bytes=500-"));
        assertEquals(new FileByteRange(null, 200L), FileWebAssembler.byteRange("bytes=-200"));
        assertNull(FileWebAssembler.byteRange(null));
        assertNull(FileWebAssembler.byteRange("bytes=0-1,5-9"));
        assertNull(FileWebAssembler.byteRange("bytes=-"));
        assertNull(FileWebAssembler.byteRange("items=0-1"));
        assertNull(FileWebAssembler.byteRange("bytes=99999999999999999999-"));
    }

    @Test
    void resolvesAgainstFileSize() {
        assertArrayEquals(new long[] {0, 99}, new FileByteRange(0L, 99L).resolve(1000));
        assertArrayEquals(new long[] {900, 999}, new FileByteRange(900L, null).resolve(1000));
        assertArrayEquals(new long[] {800, 999}, new FileByteRange(null, 200L).resolve(1000));
        assertArrayEquals(new long[] {0, 999}, new FileByteRange(null, 5000L).resolve(1000));
        assertArrayEquals(new long[] {10, 999}, new FileByteRange(10L, 99999L).resolve(1000));
        assertNull(new FileByteRange(1000L, null).resolve(1000));
        assertNull(new FileByteRange(50L, 10L).resolve(1000));
        assertNull(new FileByteRange(0L, 1L).resolve(0));
    }
}
