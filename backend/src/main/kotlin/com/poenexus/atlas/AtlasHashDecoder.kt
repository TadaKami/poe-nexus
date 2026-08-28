package com.poenexus.atlas

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Inflater

/** Декодер хэша атлас-дерева (base64url + zlib + UInt16BE ID нод). */
object AtlasHashDecoder {

    fun extractHash(url: String): String {
        val u = url.trim()
        val afterHash = u.substringAfter('#', "")
        if (afterHash.isNotBlank()) return afterHash
        val last = u.substringAfterLast('/')
        return if (last.isNotBlank() && !last.contains('.')) last else ""
    }

    /** Кандидаты (offset заголовка -> ID), сервис выберет лучший по пересечению с деревом. */
    fun decodeCandidates(hash: String): List<Pair<Int, List<Int>>> {
        if (hash.isBlank()) return emptyList()
        val std = hash.replace('-', '+').replace('_', '/')
        val padded = std.padEnd((std.length + 3) / 4 * 4, '=')
        val bytes = try {
            inflate(Base64.getDecoder().decode(padded))
        } catch (e: Exception) {
            return emptyList()
        }
        val result = mutableListOf<Pair<Int, List<Int>>>()
        for (off in 1..3) {
            val rest = bytes.size - off
            if (rest <= 0 || rest % 2 != 0) continue
            val ids = mutableListOf<Int>()
            var j = off
            while (j + 1 < bytes.size) {
                ids += ((bytes[j].toInt() and 0xFF) shl 8) or (bytes[j + 1].toInt() and 0xFF)
                j += 2
            }
            if (ids.isNotEmpty()) result += off to ids
        }
        return result
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        inflater.setInput(data)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && inflater.needsInput()) break
            out.write(buf, 0, n)
        }
        inflater.end()
        return out.toByteArray()
    }
}