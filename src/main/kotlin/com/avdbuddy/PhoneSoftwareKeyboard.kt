package com.avdbuddy

import java.awt.event.KeyEvent

/**
 * Toggles the on-screen keyboard of a phone while its screen is shared.
 *
 * Screen sharing attaches a hardware keyboard, which puts Gboard in its physical keyboard mode.
 * In that mode Gboard ignores `show_ime_with_hard_keyboard` and keeps reporting itself as shown
 * even when collapsed to its floating button, so neither the setting nor `mInputShown` can be used.
 * Instead:
 * - [isOnScreen] checks the keyboard's insets, the same signal apps get from `WindowInsets`.
 * - [show] types Gboard's own Alt+K shortcut on the attached keyboard.
 * - [hide] presses BACK, which Android hands to the keyboard first while it is on screen.
 *
 * BACK is only sent when the keyboard is known to cover the app; otherwise it would navigate the app back.
 */
internal class PhoneSoftwareKeyboard(
    private val serial: String,
    private val android: AndroidService,
    private val keyboard: DeviceMirror,
) {
    fun toggle() = if (isOnScreen()) hide() else show()

    /** Unreadable state counts as hidden so that [toggle] never sends BACK by mistake. */
    fun isOnScreen(): Boolean =
        runCatching { ImeWindow.parse(shell("dumpsys", "window", "InputMethod"))?.coversApp }.getOrNull() ?: false

    fun show() {
        val alt = physicalKeyUsage(KeyEvent.VK_ALT)!!
        val k = physicalKeyUsage(KeyEvent.VK_K)!!
        keyboard.releaseKeyboard()
        listOf(alt to true, k to true, k to false, alt to false).forEach { (usage, down) -> keyboard.keyboardKey(usage, down) }
    }

    fun hide() {
        shell("input", "keyevent", "KEYCODE_BACK")
    }

    private fun shell(vararg arguments: String): String =
        android.command(android.toolchain.adb, listOf("-s", serial, "shell") + arguments)
}

/**
 * The IME window as reported by `dumpsys window InputMethod`.
 *
 * Apps read the keyboard's insets from `WindowInsets`, which only exists inside their own process.
 * From adb the same insets appear in the window dump: the window starts at [top], the visible keyboard
 * starts [visibleInset] pixels into it, and the app area ends at [appBottom].
 */
internal data class ImeWindow(val visible: Boolean, val top: Int, val visibleInset: Int, val appBottom: Int) {
    val coversApp get() = visible && top + visibleInset < appBottom

    companion object {
        fun parse(dump: String): ImeWindow? {
            fun int(pattern: String) = Regex(pattern).find(dump)?.groupValues?.get(1)?.toInt()
            return ImeWindow(
                visible = "isVisible=true" in dump,
                top = int("""mFrame=\[-?\d+,(-?\d+)\]""") ?: return null,
                visibleInset = int("""mGivenVisibleInsets=\[-?\d+,(-?\d+)\]""") ?: return null,
                appBottom = int("""mAppBounds=Rect\(-?\d+, -?\d+ - -?\d+, (-?\d+)\)""") ?: return null,
            )
        }
    }
}
