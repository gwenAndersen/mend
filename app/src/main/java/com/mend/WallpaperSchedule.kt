package com.mend

import android.net.Uri

data class WallpaperSchedule(
    val uri: Uri,
    val hour: Int,
    val minute: Int
)
