package com.example.pkg2mpkg

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object PkgConverter {

    enum class WallpaperType {
        Video,
        Image,
        Scene,
        Web,
        Application,
        Unknown
    }

    data class Analysis(
        val magic: String,
        val entryCount: Int,
        val type: WallpaperType,
        val typeReason: String,
        val projectTitle: String? = null,
        val canTryConvert: Boolean,
        val entries: List<String>
    )

    data class Result(
        val success: Boolean,
        val message: String,
        val outputUri: Uri? = null
    )

    @Throws(PkgFormat.PkgException::class)
    fun analyze(pkg: PkgFormat.Package): Analysis {
        val entries = pkg.entries.map { it.fullPath }
        val exts = entries.map { it.substringAfterLast('.', "") }.filter { it.isNotEmpty() }
            .groupingBy { it.lowercase() }.eachCount()

        var title: String? = null
        val projectEntry = pkg.entries.find { it.fullPath.equals("project.json", true) }
        if (projectEntry != null) {
            try {
                val json = JSONObject(projectEntry.bytes.toString(Charsets.UTF_8))
                title = json.optString("title", null)
            } catch (_: Exception) {
            }
        }

        val type: WallpaperType
        val reason: String
        val canTry: Boolean

        when {
            entries.any { it.endsWith(".html", true) || it.endsWith(".js", true) } -> {
                type = WallpaperType.Web
                reason = "检测到网页壁纸（HTML/JS）。安卓 Wallpaper Engine 可能不支持或需要特殊处理。"
                canTry = true
            }
            entries.any { it.endsWith(".exe", true) || it.endsWith(".dll", true) } -> {
                type = WallpaperType.Application
                reason = "检测到应用程序壁纸（.exe/.dll）。安卓无法运行 Windows 程序，无法转换。"
                canTry = false
            }
            entries.any { it.endsWith(".tex", true) } ||
                    entries.any { it.endsWith(".json", true) && !it.equals("project.json", true) } -> {
                type = WallpaperType.Scene
                reason = "检测到场景壁纸（纹理/材质/脚本）。本工具只能重新打包，无法渲染为移动版视频。"
                canTry = true
            }
            exts.keys.any { it in listOf("mp4", "webm", "mov", "mkv") } -> {
                type = WallpaperType.Video
                reason = "检测到视频壁纸，可直接重新打包为 .mpkg。"
                canTry = true
            }
            exts.keys.any { it in listOf("png", "jpg", "jpeg", "gif", "webp") } -> {
                type = WallpaperType.Image
                reason = "检测到图片/GIF 壁纸，可直接重新打包为 .mpkg。"
                canTry = true
            }
            else -> {
                type = WallpaperType.Unknown
                reason = "无法识别壁纸类型。可尝试重新打包，但不保证 Wallpaper Engine 安卓版能导入。"
                canTry = true
            }
        }

        return Analysis(
            magic = pkg.magic,
            entryCount = pkg.entries.size,
            type = type,
            typeReason = reason,
            projectTitle = title,
            canTryConvert = canTry,
            entries = entries
        )
    }

    /**
     * Convert a PKG input stream to an MPKG file in the app's cache directory.
     * The output content is currently a repack of the original entries with the
     * extension changed to .mpkg. Scene/Application wallpapers still require
     * the Windows Wallpaper Engine renderer to be truly converted to a mobile
     * format.
     */
    suspend fun convert(
        context: Context,
        inputUri: Uri,
        outputName: String
    ): Result = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val pkg = contentResolver.openInputStream(inputUri)?.use { PkgFormat.read(it) }
                ?: return@withContext Result(false, "无法打开输入文件")

            val analysis = analyze(pkg)
            if (!analysis.canTryConvert) {
                return@withContext Result(false, analysis.typeReason)
            }

            // Repack as MPKG: keep the same PKG format, just change the file extension.
            // Wallpaper Engine mobile checks the extension and possibly the contents.
            val safeName = outputName.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5._-]"), "_")
                .let { if (it.endsWith(".mpkg", true)) it else "$it.mpkg" }

            val outFile = File(context.cacheDir, safeName)
            FileOutputStream(outFile).use { out ->
                PkgFormat.write(pkg, out)
            }

            // Copy to a public-ish URI if possible via MediaStore or SAF would be the
            // Android-idiomatic way, but for this sample we return a file:// URI and
            // rely on the UI to share/save it.
            val outUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )

            val msg = buildString {
                append("转换完成：${analysis.typeReason}\n")
                append("Magic: ${analysis.magic}\n")
                append("条目数: ${analysis.entryCount}\n")
                analysis.projectTitle?.let { append("标题: $it\n") }
                append("输出: ${outFile.name}")
            }

            Result(true, msg, outUri)
        } catch (e: PkgFormat.PkgException) {
            Result(false, "PKG 解析失败：${e.message}")
        } catch (e: Exception) {
            Result(false, "转换失败：${e.message}")
        }
    }

    /**
     * Non-suspend variant for direct file paths (testing / internal use).
     */
    fun convertFile(input: File, output: File): Result {
        return try {
            val pkg = input.inputStream().use { PkgFormat.read(it) }
            val analysis = analyze(pkg)
            if (!analysis.canTryConvert) {
                return Result(false, analysis.typeReason)
            }
            output.outputStream().use { out ->
                PkgFormat.write(pkg, out)
            }
            Result(true, "已输出到 ${output.absolutePath}\n${analysis.typeReason}")
        } catch (e: Exception) {
            Result(false, e.message ?: "未知错误")
        }
    }
}
