package com.matteo.rosterenhancer.util

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual fun shareText(text: String) {
    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    val activityViewController = UIActivityViewController(
        activityItems = listOf(text),
        applicationActivities = null
    )
    rootViewController.presentViewController(
        viewControllerToPresent = activityViewController,
        animated = true,
        completion = null
    )
}
