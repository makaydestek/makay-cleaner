package com.makay.cleaner.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.fragment.app.FragmentActivity

/** CompositionLocal ile Activity erişimi — MainActivity.instance WeakReference yerine. */
val LocalHostActivity = staticCompositionLocalOf<FragmentActivity?> { null }
