package com.example.tiletester

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.core.graphics.withSave
import java.io.File

object IconHelper {

    private fun dpToPx(ctx: Context, dp: Int): Int {
        return (dp * ctx.resources.displayMetrics.density).toInt()
    }

    /** 透图: 透明底 + 白字，可自定义大小 */
    fun transparentBitmap(context: Context, text: String, sizeDp: Int = 48): Icon {
        val size = dpToPx(context, sizeDp)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 透明背景，不画底色
        p.color = 0xFFFFFFFF.toInt()
        // 根据长度调整字号，2-3个字也能看清
        p.textSize = size * when (text.length) {
            1 -> 0.55f
            2 -> 0.42f
            else -> 0.32f
        }
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        val m = p.fontMetrics
        c.drawText(text, size / 2f, size / 2f - (m.ascent + m.descent) / 2f, p)

        return Icon.createWithBitmap(bmp)
    }

    /** Emoji: 透明底 + 白色 Emoji，可自定义大小 */
    fun emojiIcon(context: Context, emoji: String, sizeDp: Int = 48): Icon {
        val size = dpToPx(context, sizeDp)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.color = 0xFFFFFFFF.toInt()
        p.textSize = size * 0.55f
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT
        val m = p.fontMetrics
        c.drawText(emoji, size / 2f, size / 2f - (m.ascent + m.descent) / 2f, p)

        return Icon.createWithBitmap(bmp)
    }

    /** 用户上传图片 → 裁圆 + 白边 + 可自定义大小 */
    fun customImageIcon(context: Context, path: String, sizeDp: Int = 48): Icon {
        val file = if (path.startsWith("file://")) File(Uri.parse(path).path!!) else File(path)
        if (!file.exists()) {
            return transparentBitmap(context, "?", sizeDp)
        }

        val src = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            ?: return transparentBitmap(context, "?", sizeDp)

        val size = dpToPx(context, sizeDp)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val circlePath = Path().apply {
            addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CCW)
        }

        canvas.withSave {
            canvas.clipPath(circlePath)
            val scale = size.toFloat() / kotlin.math.min(src.width, src.height)
            val dx = (size - src.width * scale) / 2f
            val dy = (size - src.height * scale) / 2f
            canvas.drawBitmap(src, null, RectF(dx, dy, dx + src.width * scale, dy + src.height * scale), paint)
        }

        // 白边
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.04f
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - paint.strokeWidth / 2, paint)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Icon.createWithAdaptiveBitmap(output)
        } else {
            Icon.createWithBitmap(output)
        }
    }

    /** 保存用户选择的图片 */
    fun saveCustomImage(context: Context, uri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val dir = File(context.filesDir, "custom_icons").apply { mkdirs() }
            val outFile = File(dir, "icon_${System.currentTimeMillis()}.png")
            java.io.FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
            input.close()
            outFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
