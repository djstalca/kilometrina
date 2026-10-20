package si.lukabencina.kilometrina.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

object AttachmentInput {
    const val MAX_BYTES = 10 * 1024 * 1024

    fun read(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES - output.size() + 1))
            if (count == -1) break
            require(output.size().toLong() + count <= MAX_BYTES) { "Priloga je večja od 10 MB." }
            output.write(buffer, 0, count)
        }
        require(output.size() > 0) { "Priloga je prazna." }
        return output.toByteArray()
    }
}
