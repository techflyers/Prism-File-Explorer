package com.techflyers.compose.file.explorer.screen.main.tab.files.posix

import android.system.OsConstants
import java.util.EnumSet

enum class PosixFileModeBit {
    SET_USER_ID,
    SET_GROUP_ID,
    STICKY,
    OWNER_READ,
    OWNER_WRITE,
    OWNER_EXECUTE,
    GROUP_READ,
    GROUP_WRITE,
    GROUP_EXECUTE,
    OTHERS_READ,
    OTHERS_WRITE,
    OTHERS_EXECUTE
}

object PosixFileMode {
    private fun Int.hasBits(bits: Int): Boolean = (this and bits) == bits

    fun fromInt(modeInt: Int): Set<PosixFileModeBit> =
        EnumSet.noneOf(PosixFileModeBit::class.java).apply {
            if (modeInt.hasBits(OsConstants.S_ISUID)) this += PosixFileModeBit.SET_USER_ID
            if (modeInt.hasBits(OsConstants.S_ISGID)) this += PosixFileModeBit.SET_GROUP_ID
            if (modeInt.hasBits(OsConstants.S_ISVTX)) this += PosixFileModeBit.STICKY
            if (modeInt.hasBits(OsConstants.S_IRUSR)) this += PosixFileModeBit.OWNER_READ
            if (modeInt.hasBits(OsConstants.S_IWUSR)) this += PosixFileModeBit.OWNER_WRITE
            if (modeInt.hasBits(OsConstants.S_IXUSR)) this += PosixFileModeBit.OWNER_EXECUTE
            if (modeInt.hasBits(OsConstants.S_IRGRP)) this += PosixFileModeBit.GROUP_READ
            if (modeInt.hasBits(OsConstants.S_IWGRP)) this += PosixFileModeBit.GROUP_WRITE
            if (modeInt.hasBits(OsConstants.S_IXGRP)) this += PosixFileModeBit.GROUP_EXECUTE
            if (modeInt.hasBits(OsConstants.S_IROTH)) this += PosixFileModeBit.OTHERS_READ
            if (modeInt.hasBits(OsConstants.S_IWOTH)) this += PosixFileModeBit.OTHERS_WRITE
            if (modeInt.hasBits(OsConstants.S_IXOTH)) this += PosixFileModeBit.OTHERS_EXECUTE
        }
}

fun Set<PosixFileModeBit>.toInt(): Int =
    ((if (contains(PosixFileModeBit.SET_USER_ID)) OsConstants.S_ISUID else 0) or
            (if (contains(PosixFileModeBit.SET_GROUP_ID)) OsConstants.S_ISGID else 0) or
            (if (contains(PosixFileModeBit.STICKY)) OsConstants.S_ISVTX else 0) or
            (if (contains(PosixFileModeBit.OWNER_READ)) OsConstants.S_IRUSR else 0) or
            (if (contains(PosixFileModeBit.OWNER_WRITE)) OsConstants.S_IWUSR else 0) or
            (if (contains(PosixFileModeBit.OWNER_EXECUTE)) OsConstants.S_IXUSR else 0) or
            (if (contains(PosixFileModeBit.GROUP_READ)) OsConstants.S_IRGRP else 0) or
            (if (contains(PosixFileModeBit.GROUP_WRITE)) OsConstants.S_IWGRP else 0) or
            (if (contains(PosixFileModeBit.GROUP_EXECUTE)) OsConstants.S_IXGRP else 0) or
            (if (contains(PosixFileModeBit.OTHERS_READ)) OsConstants.S_IROTH else 0) or
            (if (contains(PosixFileModeBit.OTHERS_WRITE)) OsConstants.S_IWOTH else 0) or
            (if (contains(PosixFileModeBit.OTHERS_EXECUTE)) OsConstants.S_IXOTH else 0))

fun Set<PosixFileModeBit>.toModeString(): String =
    StringBuilder()
        .append(if (contains(PosixFileModeBit.OWNER_READ)) 'r' else '-')
        .append(if (contains(PosixFileModeBit.OWNER_WRITE)) 'w' else '-')
        .apply {
            val hasSetUserIdBit = contains(PosixFileModeBit.SET_USER_ID)
            append(
                if (contains(PosixFileModeBit.OWNER_EXECUTE)) {
                    if (hasSetUserIdBit) 's' else 'x'
                } else {
                    if (hasSetUserIdBit) 'S' else '-'
                }
            )
        }
        .append(if (contains(PosixFileModeBit.GROUP_READ)) 'r' else '-')
        .append(if (contains(PosixFileModeBit.GROUP_WRITE)) 'w' else '-')
        .apply {
            val hasSetGroupIdBit = contains(PosixFileModeBit.SET_GROUP_ID)
            append(
                if (contains(PosixFileModeBit.GROUP_EXECUTE)) {
                    if (hasSetGroupIdBit) 's' else 'x'
                } else {
                    if (hasSetGroupIdBit) 'S' else '-'
                }
            )
        }
        .append(if (contains(PosixFileModeBit.OTHERS_READ)) 'r' else '-')
        .append(if (contains(PosixFileModeBit.OTHERS_WRITE)) 'w' else '-')
        .apply {
            val hasStickyBit = contains(PosixFileModeBit.STICKY)
            append(
                if (contains(PosixFileModeBit.OTHERS_EXECUTE)) {
                    if (hasStickyBit) 't' else 'x'
                } else {
                    if (hasStickyBit) 'T' else '-'
                }
            )
        }
        .toString()

fun formatPosixMode(modeInt: Int): String {
    val modeBits = PosixFileMode.fromInt(modeInt)
    val octal = String.format("%04o", modeInt and 0xFFF)
    return "${modeBits.toModeString()} ($octal)"
}
