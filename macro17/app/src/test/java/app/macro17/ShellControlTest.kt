package app.macro17

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.interfaces.RSAPublicKey
import java.util.Base64

class ShellControlTest {
    private class Rec : Shell { val cmds = ArrayList<String>(); override fun run(command: String): ByteArray { synchronized(cmds) { cmds += command }; return ByteArray(0) } }

    @Test fun tapIsAZeroLengthSwipeAndKeysMapToInputCommands() {
        val r = Rec()
        ShellInput.gesture(r, listOf(Stroke(10, 20, 10, 20)), 5)
        assertEquals(listOf("input touchscreen swipe 10 20 10 20 5"), r.cmds)
        assertEquals("input keyevent 4", ShellInput.key(1)); assertEquals("input keyevent 3", ShellInput.key(2))
        assertEquals("input keyevent 187", ShellInput.key(3)); assertEquals("input keyevent 120", ShellInput.key(KEY_SCREENSHOT))
    }

    @Test fun pinchRunsOneCommandPerFinger() {
        val r = Rec()
        ShellInput.gesture(r, listOf(Stroke(1, 2, 3, 4), Stroke(5, 6, 7, 8)), 300)
        assertEquals(setOf("input touchscreen swipe 1 2 3 4 300", "input touchscreen swipe 5 6 7 8 300"), r.cmds.toSet())
    }

    @Test fun adbPacketHeaderRoundTrips() {
        val raw = AdbProtocol.encode(AdbProtocol.OPEN, 1, 0, "shell:ls\u0000".toByteArray())
        val m = AdbProtocol.read(raw.inputStream())
        assertEquals(AdbProtocol.OPEN, m.cmd); assertEquals(1, m.arg0); assertArrayEquals("shell:ls\u0000".toByteArray(), m.data)
        val h = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(AdbProtocol.OPEN.inv(), h.getInt(20))                       // magic = comando xor 0xffffffff
    }

    @Test fun publicKeyBlobFollowsTheAndroidLayout() {
        val key = AdbProtocol.newKey()
        val blob = AdbProtocol.publicKeyBlob(key.public as RSAPublicKey)
        val text = String(blob).trimEnd('\u0000')
        val raw = Base64.getDecoder().decode(text.substringBefore(' '))
        assertEquals(4 + 4 + 256 + 256 + 4, raw.size)
        val b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(64, b.int)
        val n0inv = b.int.toLong() and 0xFFFFFFFFL
        val n0 = (key.public as RSAPublicKey).modulus.toLong().let { it and 0xFFFFFFFFL }
        assertEquals(0xFFFFFFFFL, (n0inv * n0) and 0xFFFFFFFFL)                // n0inv * n[0] = -1 (mod 2^32)
        assertTrue(text.endsWith("macro17@localhost"))
    }

    @Test fun keyPairSurvivesStorage() {
        val k = AdbProtocol.newKey()
        val back = AdbProtocol.decodeKey(AdbProtocol.encodeKey(k))!!
        assertEquals((k.public as RSAPublicKey).modulus, (back.public as RSAPublicKey).modulus)
        val sig = AdbProtocol.sign(back, ByteArray(20) { it.toByte() })
        assertEquals(256, sig.size)
    }
}
