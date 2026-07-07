package com.synervoz.karaokeapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.synervoz.karaokeapp.R
import kotlin.math.roundToInt

/** Formats a duration in seconds as e.g. "2m 19s", matching the original UI. */
fun formatTime(seconds: Double): String {
    val safe = if (seconds.isFinite() && seconds > 0) seconds else 0.0
    return "${(safe / 60).toInt()}m ${(safe % 60).roundToInt()}s"
}

/** The Switchboard SDK wordmark shown at the top of every screen. */
@Composable
fun SwitchboardLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.switchboard_sdk_logo_text),
        contentDescription = "Switchboard SDK",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(40.dp),
    )
}
