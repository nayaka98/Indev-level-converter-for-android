package com.example

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

sealed class NbtTag {
    abstract val type: Byte
}

data class NbtByte(var value: Byte) : NbtTag() { override val type: Byte get() = 1 }
data class NbtShort(var value: Short) : NbtTag() { override val type: Byte get() = 2 }
data class NbtInt(var value: Int) : NbtTag() { override val type: Byte get() = 3 }
data class NbtLong(var value: Long) : NbtTag() { override val type: Byte get() = 4 }
data class NbtFloat(var value: Float) : NbtTag() { override val type: Byte get() = 5 }
data class NbtDouble(var value: Double) : NbtTag() { override val type: Byte get() = 6 }
data class NbtByteArray(var value: ByteArray) : NbtTag() {
    override val type: Byte get() = 7
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as NbtByteArray
        return value.contentEquals(other.value)
    }
    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}
data class NbtString(var value: String) : NbtTag() { override val type: Byte get() = 8 }
data class NbtList(var listType: Byte, val value: MutableList<NbtTag> = mutableListOf()) : NbtTag() { override val type: Byte get() = 9 }
data class NbtCompound(val value: LinkedHashMap<String, NbtTag> = LinkedHashMap()) : NbtTag() { override val type: Byte get() = 10 }
data class NbtIntArray(var value: IntArray) : NbtTag() {
    override val type: Byte get() = 11
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as NbtIntArray
        return value.contentEquals(other.value)
    }
    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}
data class NbtLongArray(var value: LongArray) : NbtTag() {
    override val type: Byte get() = 12
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as NbtLongArray
        return value.contentEquals(other.value)
    }
    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}

fun NbtCompound.getCompound(name: String) = value[name] as? NbtCompound ?: NbtCompound()
fun NbtCompound.getList(name: String) = value[name] as? NbtList ?: NbtList(0)
fun NbtCompound.getByteArray(name: String) = (value[name] as? NbtByteArray)?.value ?: ByteArray(0)
fun NbtCompound.getShort(name: String) = (value[name] as? NbtShort)?.value ?: 0.toShort()
fun NbtCompound.getLong(name: String) = (value[name] as? NbtLong)?.value ?: 0L
fun NbtCompound.getInt(name: String) = (value[name] as? NbtInt)?.value ?: 0
fun NbtCompound.getFloat(name: String) = (value[name] as? NbtFloat)?.value ?: 0f
fun NbtCompound.getDouble(name: String) = (value[name] as? NbtDouble)?.value ?: 0.0

object NbtIo {
    fun read(stream: InputStream): Pair<String, NbtCompound>? {
        val din = DataInputStream(GZIPInputStream(stream))
        val type = din.readByte()
        if (type.toInt() == 0) return "" to NbtCompound()
        val name = din.readUTF()
        val tag = readTagPayload(type, din)
        return name to (tag as NbtCompound)
    }

    fun write(name: String, tag: NbtCompound, stream: OutputStream) {
        val gout = GZIPOutputStream(stream)
        val dout = DataOutputStream(gout)
        dout.writeByte(10)
        dout.writeUTF(name)
        writeTagPayload(tag, dout)
        dout.flush()
        gout.finish() // write trailer without closing underlying stream
    }

    private fun readTagPayload(type: Byte, din: DataInputStream): NbtTag {
        return when (type.toInt()) {
            1 -> NbtByte(din.readByte())
            2 -> NbtShort(din.readShort())
            3 -> NbtInt(din.readInt())
            4 -> NbtLong(din.readLong())
            5 -> NbtFloat(din.readFloat())
            6 -> NbtDouble(din.readDouble())
            7 -> {
                val len = din.readInt()
                val arr = ByteArray(len)
                din.readFully(arr)
                NbtByteArray(arr)
            }
            8 -> NbtString(din.readUTF())
            9 -> {
                val listType = din.readByte()
                val len = din.readInt()
                val list = NbtList(listType)
                for (i in 0 until len) {
                    list.value.add(readTagPayload(listType, din))
                }
                list
            }
            10 -> {
                val comp = NbtCompound()
                while (true) {
                    val t = din.readByte()
                    if (t.toInt() == 0) break
                    val n = din.readUTF()
                    comp.value[n] = readTagPayload(t, din)
                }
                comp
            }
            11 -> {
                val len = din.readInt()
                val arr = IntArray(len)
                for (i in 0 until len) arr[i] = din.readInt()
                NbtIntArray(arr)
            }
            12 -> {
                val len = din.readInt()
                val arr = LongArray(len)
                for (i in 0 until len) arr[i] = din.readLong()
                NbtLongArray(arr)
            }
            else -> throw IllegalArgumentException("Unknown tag type $type")
        }
    }

    private fun writeTagPayload(tag: NbtTag, dout: DataOutputStream) {
        when (tag) {
            is NbtByte -> dout.writeByte(tag.value.toInt())
            is NbtShort -> dout.writeShort(tag.value.toInt())
            is NbtInt -> dout.writeInt(tag.value)
            is NbtLong -> dout.writeLong(tag.value)
            is NbtFloat -> dout.writeFloat(tag.value)
            is NbtDouble -> dout.writeDouble(tag.value)
            is NbtByteArray -> {
                dout.writeInt(tag.value.size)
                dout.write(tag.value)
            }
            is NbtString -> dout.writeUTF(tag.value)
            is NbtList -> {
                dout.writeByte(tag.listType.toInt())
                dout.writeInt(tag.value.size)
                for (t in tag.value) writeTagPayload(t, dout)
            }
            is NbtCompound -> {
                for ((n, t) in tag.value) {
                    dout.writeByte(t.type.toInt())
                    dout.writeUTF(n)
                    writeTagPayload(t, dout)
                }
                dout.writeByte(0)
            }
            is NbtIntArray -> {
                dout.writeInt(tag.value.size)
                for (i in tag.value) dout.writeInt(i)
            }
            is NbtLongArray -> {
                dout.writeInt(tag.value.size)
                for (i in tag.value) dout.writeLong(i)
            }
        }
    }
}
