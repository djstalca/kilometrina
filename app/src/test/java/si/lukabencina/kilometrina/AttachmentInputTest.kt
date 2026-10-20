package si.lukabencina.kilometrina

import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test
import si.lukabencina.kilometrina.data.AttachmentInput

class AttachmentInputTest {
    @Test fun preservesEveryByteAtTheSizeLimit() {
        val original = ByteArray(AttachmentInput.MAX_BYTES) { it.toByte() }
        assertArrayEquals(original, AttachmentInput.read(original.inputStream()))
    }

    @Test fun rejectsEmptyAndStopsReadingOversizedStreamsAtLimitPlusOne() {
        assertThrows(IllegalArgumentException::class.java) { AttachmentInput.read(byteArrayOf().inputStream()) }
        var consumed = 0L
        val unlimited = object : InputStream() {
            override fun read(): Int { consumed++; return 1 }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                bytes.fill(1, offset, offset + length)
                consumed += length
                return length
            }
        }
        assertThrows(IllegalArgumentException::class.java) { AttachmentInput.read(unlimited) }
        assertEquals(AttachmentInput.MAX_BYTES + 1L, consumed)
    }
}
