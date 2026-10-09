package com.evaitec.netmovies.tv.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.input.pointer.PointerIcon

/**
 * Big ring cursor for a Bluetooth mouse (the watch air mouse). Android's stock arrow is
 * ~20 px — lost on a TV from 3 m. Hotspot is the ring's centre.
 */
fun buyukImlec(): PointerIcon {
    val boy = 72
    val bmp = Bitmap.createBitmap(boy, boy, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val m = boy / 2f
    p.color = 0xCC000000.toInt(); p.strokeWidth = 12f; c.drawCircle(m, m, 26f, p)
    p.color = 0xFFFFFFFF.toInt(); p.strokeWidth = 6f; c.drawCircle(m, m, 26f, p)
    p.style = Paint.Style.FILL
    p.color = 0xFF8B5CF6.toInt(); c.drawCircle(m, m, 7f, p)
    return PointerIcon(android.view.PointerIcon.create(bmp, m, m))
}
