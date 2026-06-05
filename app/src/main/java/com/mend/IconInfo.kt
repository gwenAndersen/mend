package com.mend

import android.graphics.Rect
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class IconInfo(
    val packageName: String,
    val text: String?,
    val boundsInScreen: Rect
) : Parcelable