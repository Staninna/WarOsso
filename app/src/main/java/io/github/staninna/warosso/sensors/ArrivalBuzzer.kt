package io.github.staninna.warosso.sensors

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator

/** One firm buzz when the user reaches the entrance. */
class ArrivalBuzzer(context: Context) {
    private val vibrator = context.getSystemService(Vibrator::class.java)

    fun buzz() {
        vibrator.vibrate(
            VibrationEffect.createOneShot(BUZZ_MS, VibrationEffect.DEFAULT_AMPLITUDE),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION),
        )
    }

    private companion object {
        const val BUZZ_MS = 450L
    }
}
