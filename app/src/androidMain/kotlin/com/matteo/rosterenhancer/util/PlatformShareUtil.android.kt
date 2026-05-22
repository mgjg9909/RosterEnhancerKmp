package com.matteo.rosterenhancer.util

import android.content.Intent
import com.matteo.rosterenhancer.RosterApplication

actual fun shareText(text: String) {
    val context = RosterApplication.appContext ?: return
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Condividi con...")
    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(shareIntent)
}
