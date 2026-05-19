package com.example.tiletester

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        val path = IconHelper.saveCustomImage(this, uri)
        if (path != null) {
            TestTileCustomImage.customPath = path
            refreshTile(TestTileCustomImage::class.java)
            Toast.makeText(this, "图片已设置", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvInfo = findViewById<TextView>(R.id.tvInfo)
        val btnRefresh = findViewById<Button>(R.id.btnRefresh)
        val btnCustomTransparent = findViewById<Button>(R.id.btnCustomTransparent)
        val btnCustomEmoji = findViewById<Button>(R.id.btnCustomEmoji)
        val btnCustomSystem = findViewById<Button>(R.id.btnCustomSystem)
        val btnCustomImage = findViewById<Button>(R.id.btnCustomImage)

        tvInfo.text = buildString {
            appendLine("ColorOS 磁贴图标测试 v3")
            appendLine("Android API: ${Build.VERSION.SDK_INT}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Model: ${Build.MODEL}")
            appendLine()
            appendLine("上一轮结论:")
            appendLine("✅ 透图(透明底+白字) 可行")
            appendLine("✅ Emoji(透明底+白Emoji) 可行")
            appendLine("✅ 系统图标 可行")
            appendLine()
            appendLine("本轮测试:")
            appendLine("• 自定义文字/Emoji/图片")
            appendLine("• 自定义图标大小(32/48/64dp)")
            appendLine("• 看大小对显示的影响")
        }

        btnRefresh.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                listOf(
                    TestTileCustomTransparent::class.java,
                    TestTileCustomEmoji::class.java,
                    TestTileCustomSystem::class.java,
                    TestTileCustomImage::class.java
                ).forEach { cls ->
                    TileService.requestListeningState(this, ComponentName(this, cls))
                }
                Toast.makeText(this, "已刷新4个磁贴", Toast.LENGTH_SHORT).show()
            }
        }

        btnCustomTransparent.setOnClickListener {
            showTransparentDialog()
        }

        btnCustomEmoji.setOnClickListener {
            showEmojiDialog()
        }

        btnCustomSystem.setOnClickListener {
            showSystemIconPicker()
        }

        btnCustomImage.setOnClickListener {
            showImageDialog()
        }
    }

    private fun refreshTile(cls: Class<*>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TileService.requestListeningState(this, ComponentName(this, cls))
        }
    }

    private fun showSizePicker(onPick: (Int) -> Unit) {
        val sizes = arrayOf("32dp (小)", "48dp (标准)", "64dp (大)")
        AlertDialog.Builder(this)
            .setTitle("选择图标大小")
            .setItems(sizes) { _, which ->
                val size = when (which) {
                    0 -> 32
                    1 -> 48
                    else -> 64
                }
                onPick(size)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showTransparentDialog() {
        val input = EditText(this)
        input.hint = "输入1-3个字符"
        input.maxLines = 1

        AlertDialog.Builder(this)
            .setTitle("自定义透图")
            .setView(input)
            .setPositiveButton("下一步") { _, _ ->
                val text = input.text.toString().trim().take(3)
                if (text.isNotEmpty()) {
                    showSizePicker { size ->
                        TestTileCustomTransparent.customText = text
                        TestTileCustomTransparent.customSize = size
                        refreshTile(TestTileCustomTransparent::class.java)
                        Toast.makeText(this, "透图: '$text' ${size}dp", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEmojiDialog() {
        val input = EditText(this)
        input.hint = "输入Emoji，如: 🐱 🔥 🇨🇳"
        input.maxLines = 1

        AlertDialog.Builder(this)
            .setTitle("自定义 Emoji")
            .setView(input)
            .setPositiveButton("下一步") { _, _ ->
                val emoji = input.text.toString().trim()
                if (emoji.isNotEmpty()) {
                    showSizePicker { size ->
                        TestTileCustomEmoji.customEmoji = emoji
                        TestTileCustomEmoji.customSize = size
                        refreshTile(TestTileCustomEmoji::class.java)
                        Toast.makeText(this, "Emoji: '$emoji' ${size}dp", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showImageDialog() {
        val options = arrayOf("选择图片", "清除图片")
        AlertDialog.Builder(this)
            .setTitle("自定义图片")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        showSizePicker { size ->
                            TestTileCustomImage.customSize = size
                            pickImageLauncher.launch("image/*")
                        }
                    }
                    1 -> {
                        TestTileCustomImage.customPath = ""
                        refreshTile(TestTileCustomImage::class.java)
                        Toast.makeText(this, "图片已清除", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSystemIconPicker() {
        val icons = listOf(
            "ic_menu_preferences" to android.R.drawable.ic_menu_preferences,
            "ic_menu_search" to android.R.drawable.ic_menu_search,
            "ic_menu_share" to android.R.drawable.ic_menu_share,
            "ic_menu_save" to android.R.drawable.ic_menu_save,
            "ic_menu_delete" to android.R.drawable.ic_menu_delete,
            "ic_menu_edit" to android.R.drawable.ic_menu_edit,
            "ic_menu_camera" to android.R.drawable.ic_menu_camera,
            "ic_menu_gallery" to android.R.drawable.ic_menu_gallery,
            "ic_menu_mapmode" to android.R.drawable.ic_menu_mapmode,
            "ic_menu_compass" to android.R.drawable.ic_menu_compass,
            "ic_menu_call" to android.R.drawable.ic_menu_call,
            "ic_menu_send" to android.R.drawable.ic_menu_send,
            "ic_menu_help" to android.R.drawable.ic_menu_help,
            "ic_menu_info_details" to android.R.drawable.ic_menu_info_details,
            "ic_menu_close_clear_cancel" to android.R.drawable.ic_menu_close_clear_cancel,
            "ic_menu_revert" to android.R.drawable.ic_menu_revert,
            "ic_menu_set_as" to android.R.drawable.ic_menu_set_as,
            "ic_menu_sort_by_size" to android.R.drawable.ic_menu_sort_by_size,
            "ic_menu_upload" to android.R.drawable.ic_menu_upload,
            "ic_menu_agenda" to android.R.drawable.ic_menu_agenda,
            "ic_menu_day" to android.R.drawable.ic_menu_day,
            "ic_menu_week" to android.R.drawable.ic_menu_week,
            "ic_menu_month" to android.R.drawable.ic_menu_month,
            "ic_menu_today" to android.R.drawable.ic_menu_today,
            "ic_menu_rotate" to android.R.drawable.ic_menu_rotate,
            "ic_menu_crop" to android.R.drawable.ic_menu_crop,
            "ic_menu_add" to android.R.drawable.ic_menu_add,
            "ic_menu_more" to android.R.drawable.ic_menu_more,
            "ic_menu_view" to android.R.drawable.ic_menu_view,
            "ic_menu_report_image" to android.R.drawable.ic_menu_report_image,
            "ic_menu_directions" to android.R.drawable.ic_menu_directions,
            "ic_menu_myplaces" to android.R.drawable.ic_menu_myplaces,
            "ic_menu_mylocation" to android.R.drawable.ic_menu_mylocation,
            "ic_menu_zoom" to android.R.drawable.ic_menu_zoom,
            "ic_menu_manage" to android.R.drawable.ic_menu_manage,
            "ic_menu_slideshow" to android.R.drawable.ic_menu_slideshow,
            "ic_menu_always_landscape_portrait" to android.R.drawable.ic_menu_always_landscape_portrait,
        )

        val names = icons.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择系统图标")
            .setItems(names) { _, which ->
                val (name, resId) = icons[which]
                TestTileCustomSystem.customResId = resId
                refreshTile(TestTileCustomSystem::class.java)
                Toast.makeText(this, "系统图标: $name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
