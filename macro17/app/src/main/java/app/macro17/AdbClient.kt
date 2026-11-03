package app.macro17

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Cliente mínimo do protocolo ADB por TCP (adbd em 127.0.0.1, ativado uma vez no computador com `adb tcpip 5555`):
 * CNXN, autenticação por chave RSA (o aparelho mostra "Permitir depuração USB?" na primeira vez) e `shell:`/`exec:`.
 * Java puro, sem Android, para o formato dos pacotes e da chave ser testado na JVM.
 */
object AdbProtocol {
    const val CNXN = 0x4e584e43; const val AUTH = 0x48545541; const val OPEN = 0x4e45504f
    const val OKAY = 0x59414b4f; const val CLSE = 0x45534c43; const val WRTE = 0x45545257
    const val VERSION = 0x01000000; const val MAXDATA = 256 * 1024
    const val AUTH_TOKEN = 1; const val AUTH_SIGNATURE = 2; const val AUTH_RSAPUBLICKEY = 3

    class Msg(val cmd: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    fun encode(cmd: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)): ByteArray {
        val b = ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.size).putInt(0 /* sem checksum: versão >= 0x01000001 */).putInt(cmd.inv())
        b.put(data)
        return b.array()
    }

    fun read(i: java.io.InputStream): Msg {
        val h = ByteArray(24); readFully(i, h)
        val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = b.int; val a0 = b.int; val a1 = b.int; val len = b.int; b.int; val magic = b.int
        if (magic != cmd.inv() || len < 0 || len > MAXDATA) throw IOException("pacote ADB inválido")
        val d = ByteArray(len); readFully(i, d)
        return Msg(cmd, a0, a1, d)
    }

    private fun readFully(i: java.io.InputStream, buf: ByteArray) {
        var n = 0
        while (n < buf.size) { val r = i.read(buf, n, buf.size - n); if (r < 0) throw IOException("conexão ADB encerrada"); n += r }
    }

    private val SHA1_PREFIX = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)

    /** O token do aparelho (20 bytes) é tratado como o resumo SHA-1 já calculado: assina DigestInfo + token, sem refazer o hash. */
    fun sign(key: KeyPair, token: ByteArray): ByteArray = Signature.getInstance("NONEwithRSA").run {
        initSign(key.private); update(SHA1_PREFIX + token); sign()
    }

    private fun le(v: BigInteger, size: Int): ByteArray {
        val be = v.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        val out = ByteArray(size); for (k in be.indices) out[k] = be[be.size - 1 - k]
        return out
    }

    /** Chave pública no formato do Android (struct RSAPublicKey: palavras, -1/n[0], módulo, R², expoente) em base64 + comentário + NUL. */
    fun publicKeyBlob(key: RSAPublicKey, comment: String = "macro17@localhost"): ByteArray {
        val n = key.modulus; val words = 64
        val n0inv = BigInteger.ONE.shiftLeft(32).subtract(n.mod(BigInteger.ONE.shiftLeft(32)).modInverse(BigInteger.ONE.shiftLeft(32))).toInt()
        val rr = BigInteger.ONE.shiftLeft(2048 * 2).mod(n)
        val b = ByteBuffer.allocate(4 + 4 + 256 + 256 + 4).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(words).putInt(n0inv).put(le(n, 256)).put(le(rr, 256)).putInt(key.publicExponent.toInt())
        return Base64.getEncoder().encode(b.array()) + " $comment".toByteArray() + byteArrayOf(0)
    }

    fun newKey(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    fun encodeKey(k: KeyPair): String = Base64.getEncoder().encodeToString(k.private.encoded) + ":" + Base64.getEncoder().encodeToString(k.public.encoded)
    fun decodeKey(s: String): KeyPair? = runCatching {
        val (p, q) = s.split(':'); val kf = KeyFactory.getInstance("RSA")
        KeyPair(kf.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(q))), kf.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(p))))
    }.getOrNull()
}

/** Uma conexão por comando (o adbd local abre rápido); a chave fica guardada, então a autorização é pedida só na primeira vez. */
class AdbClient(private val key: KeyPair, private val port: Int = 5555) {
    fun run(command: String, timeoutMs: Int = 15_000): ByteArray = Socket().use { s ->
        s.connect(InetSocketAddress("127.0.0.1", port), 3000); s.soTimeout = timeoutMs; s.tcpNoDelay = true
        val i = s.getInputStream(); val o = s.getOutputStream()
        o.write(AdbProtocol.encode(AdbProtocol.CNXN, AdbProtocol.VERSION, AdbProtocol.MAXDATA, "host::\u0000".toByteArray())); o.flush()
        var triedSig = false
        auth@ while (true) {
            val m = AdbProtocol.read(i)
            when (m.cmd) {
                AdbProtocol.CNXN -> break@auth
                AdbProtocol.AUTH -> {
                    if (!triedSig) { triedSig = true; o.write(AdbProtocol.encode(AdbProtocol.AUTH, AdbProtocol.AUTH_SIGNATURE, 0, AdbProtocol.sign(key, m.data))) }
                    else { o.write(AdbProtocol.encode(AdbProtocol.AUTH, AdbProtocol.AUTH_RSAPUBLICKEY, 0, AdbProtocol.publicKeyBlob(key.public as RSAPublicKey))); s.soTimeout = 60_000 /* tempo para tocar em Permitir */ }
                    o.flush()
                }
                else -> throw IOException("resposta ADB inesperada")
            }
        }
        s.soTimeout = timeoutMs
        o.write(AdbProtocol.encode(AdbProtocol.OPEN, 1, 0, "$command\u0000".toByteArray())); o.flush()
        val out = ByteArrayOutputStream(); var remote = 0
        read@ while (true) {
            val m = AdbProtocol.read(i)
            when (m.cmd) {
                AdbProtocol.OKAY -> remote = m.arg0
                AdbProtocol.WRTE -> { out.write(m.data); remote = m.arg0; o.write(AdbProtocol.encode(AdbProtocol.OKAY, 1, remote)); o.flush() }
                AdbProtocol.CLSE -> break@read
                else -> {}
            }
        }
        out.toByteArray()
    }
}
