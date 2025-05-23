package com.movtery.zalithlauncher.utils.file

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.movtery.zalithlauncher.utils.string.StringUtils.Companion.compareChar
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun compareSHA1(file: File, sourceSHA: String?, default: Boolean = true): Boolean {
    val computedSHA = runCatching {
        FileInputStream(file).use { fis ->
            String(Hex.encodeHex(DigestUtils.sha1(fis)))
        }
    }.getOrElse { e ->
        Log.i("CompareSHA1", "An exception occurred while reading, returning the default value.", e)
        return default
    }

    return sourceSHA?.equals(computedSHA, ignoreCase = true) ?: default
}

@SuppressLint("DefaultLocale")
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB")
    var unitIndex = 0
    var value = bytes.toDouble()
    //循环获取合适的单位
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024.0
        unitIndex++
    }
    return String.format("%.2f %s", value, units[unitIndex])
}

fun sortWithFileName(o1: File, o2: File): Int {
    val isDir1 = o1.isDirectory
    val isDir2 = o2.isDirectory

    //目录排在前面，文件排在后面
    if (isDir1 && !isDir2) return -1
    if (!isDir1 && isDir2) return 1

    return compareChar(o1.name, o2.name)
}

const val INVALID_CHARACTERS_REGEX = "[\\\\/:*?\"<>|\\t\\n]"

@Throws(InvalidFilenameException::class)
fun checkFilenameValidity(str: String) {
    val illegalCharsRegex = INVALID_CHARACTERS_REGEX.toRegex()

    val illegalChars = illegalCharsRegex.findAll(str)
        .map { it.value }
        .distinct()
        .joinToString("")

    if (illegalChars.isNotEmpty()) {
        throw InvalidFilenameException("The filename contains illegal characters", illegalChars)
    }

    if (str.length > 255) {
        throw InvalidFilenameException("Invalid filename length", str.length)
    }
}

/**
 * Same as ensureDirectorySilently(), but throws an IOException telling why the check failed.
 * [Modified from PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher/blob/e492223/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/FileUtils.java#L61-L71)
 * @throws IOException when the checks fail
 */
@Throws(IOException::class)
fun File.ensureDirectory(): File {
    if (isFile) throw IOException("Target directory is a file")
    if (exists()) {
        if (!canWrite()) throw IOException("Target directory is not writable")
    } else {
        if (!mkdirs()) throw IOException("Unable to create target directory")
    }
    return this
}

/**
 * Same as ensureParentDirectorySilently(), but throws an IOException telling why the check failed.
 * [Modified from PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher/blob/e492223/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/FileUtils.java#L73-L82)
 * @throws IOException when the checks fail
 */
@Throws(IOException::class)
fun File.ensureParentDirectory(): File {
    val parentDir: File = parentFile ?: throw IOException("targetFile does not have a parent")
    parentDir.ensureDirectory()
    return this
}

fun File.ensureDirectorySilently(): Boolean {
    if (isFile) return false
    return if (exists()) canWrite()
    else mkdirs()
}

fun File.child(vararg paths: String) = File(this, paths.joinToString(File.separator))

fun InputStream.readString(): String {
    return use {
        IOUtils.toString(this, StandardCharsets.UTF_8)
    }
}

fun shareFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooserIntent = Intent.createChooser(shareIntent, file.name)
    context.startActivity(chooserIntent)
}

fun zipDirRecursive(baseDir: File, current: File, zipOut: ZipOutputStream) {
    current.listFiles()?.forEach { file ->
        val entryName = file.relativeTo(baseDir).invariantSeparatorsPath
        if (file.isDirectory) {
            zipOut.putNextEntry(ZipEntry("$entryName/"))
            zipOut.closeEntry()
            zipDirRecursive(baseDir, file, zipOut)
        } else {
            zipOut.putNextEntry(ZipEntry(entryName))
            file.inputStream().copyTo(zipOut)
            zipOut.closeEntry()
        }
    }
}