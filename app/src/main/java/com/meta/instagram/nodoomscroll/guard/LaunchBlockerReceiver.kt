package com.meta.instagram.nodoomscroll.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Second enforcement layer: while the guard is armed we ask the system to keep
 * the official Instagram app out of reach (it has an unblockable Reels feed).
 *
 * Note: a normal app cannot truly block another app's launch without Device
 * Admin / Device Policy ownership. We therefore surface a lock screen and send
 * the user home; full blocking requires Device Admin opt-in (see GuardManager).
 */
class LaunchBlockerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val i = Intent(context, LockdownActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(LockdownActivity.EXTRA_REASON, LockdownActivity.REASON_TAB)
        }
        context.startActivity(i)
    }
}
