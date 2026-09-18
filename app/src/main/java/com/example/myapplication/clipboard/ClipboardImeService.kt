package com.example.myapplication.clipboard

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import com.example.myapplication.runtime.AppStatusBus

/**
 * 可选的剪贴板通道输入法。
 *
 * Android 10 起后台应用无法读取剪贴板，唯一稳定豁免是“默认输入法”。
 * 本输入法提供一个极简英文键盘：用户把它设为默认输入法后，
 * 后台 [com.example.myapplication.service.MqttForegroundService] 即可持续读取剪贴板。
 */
class ClipboardImeService : InputMethodService() {

    override fun onCreateInputView(): View = buildKeyboard()

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        AppStatusBus.log("剪贴板输入法已就绪")
    }

    private fun buildKeyboard(): View {
        val rows = arrayOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E9ECF1"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 顶部提示行
        root.addView(makeKey("剪贴板已同步 · 切换输入法", 1f).apply {
            setOnClickListener { showPicker() }
            alpha = 0.75f
        })

        rows.forEach { row ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            row.forEach { ch -> line.addView(makeKey(ch.toString(), 1f)) }
            root.addView(line)
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        bottom.addView(makeKey("🌐", 1.2f).apply { setOnClickListener { showPicker() } })
        bottom.addView(makeKey("空格", 3f).apply { setOnClickListener { commit(" ") } })
        bottom.addView(makeKey("⌫", 1.5f).apply { setOnClickListener { delete() } })
        bottom.addView(makeKey("↵", 1.5f).apply { setOnClickListener { commit("\n") } })
        root.addView(bottom)

        return root
    }

    private fun makeKey(label: String, weight: Float): Button {
        val key = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setPadding(0, 0, 0, 0)
            minHeight = dp(46)
            minimumHeight = dp(46)
        }
        key.layoutParams = LinearLayout.LayoutParams(0, dp(46), weight)
        if (label.length == 1 || label == "空格" || label == "⌫" || label == "↵") {
            key.setOnClickListener {
                when (label) {
                    "↵" -> commit("\n")
                    "⌫" -> delete()
                    "空格" -> commit(" ")
                    "🌐" -> showPicker()
                    else -> commit(label)
                }
            }
        }
        return key
    }

    private fun commit(text: String) {
        runCatching { currentInputConnection?.commitText(text, 1) }
    }

    private fun delete() {
        runCatching { currentInputConnection?.deleteSurroundingText(1, 0) }
    }

    private fun showPicker() {
        runCatching {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
