// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import fr.codetics.evapp.core.AppConfiguration
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

object DemoSupport {
    const val available = true
    const val EMAIL = "thomas.bernard@demo.codetics.fr"
    const val PASSWORD = "demo-account-only-in-debug-builds"
    const val OWN_CARD = "04A1B2C3D4E580"
    const val ORDERED_CARD = "04C7D8E9FA1B80"
    const val ORDERED_CARD_NUMBER = "CARTE-2210"
    const val EVSE_ID = "5f9f1c1e-7c3a-4d3a-9c55-0a7e6b1d2f10"
    const val PORTAL_LINK = "https://demo.ev-app.invalid/charge/demo/$EVSE_ID"
    const val USER_ID = "11111111-2222-4333-8444-555555555555"
    const val TENANT_ID = "99999999-2222-4333-8444-555555555555"

    fun configuration(): AppConfiguration? =
        AppConfiguration(
            apiUrl = "https://demo.ev-app.invalid",
            portalUrl = "https://demo.ev-app.invalid",
            workspaceSlug = "demo",
            brandName = "Réseau Démo",
            supportEmail = "support@codetics.fr",
            privacyPolicyUrl = "https://codetics.fr",
            mapStyleUrl = "https://tiles.openfreemap.org/styles/liberty",
        )

    private val server = DemoServer()

    fun interceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val body = request.body?.let { content -> Buffer().also(content::writeTo).readByteArray() }
        val (status, data, type) = server.handle(request.method, request.url.encodedPath, request.url, body)
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(status)
            .message("demo")
            .body(data.toResponseBody(type.toMediaType()))
            .build()
    }

    fun stationPhoto(): Bitmap {
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, 900f, Color.rgb(170, 210, 240), Color.rgb(235, 240, 235), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1200f, 900f, paint)
        paint.shader = null
        paint.color = Color.rgb(90, 95, 100)
        canvas.drawRect(0f, 720f, 1200f, 900f, paint)
        paint.color = Color.rgb(0, 130, 54)
        canvas.drawRoundRect(480f, 220f, 720f, 760f, 28f, 28f, paint)
        paint.color = Color.rgb(20, 30, 25)
        canvas.drawRoundRect(515f, 270f, 685f, 410f, 14f, 14f, paint)
        paint.color = Color.rgb(124, 207, 0)
        val bolt = android.graphics.Path().apply {
            moveTo(610f, 450f); lineTo(555f, 560f); lineTo(600f, 560f); lineTo(585f, 650f); lineTo(650f, 530f); lineTo(605f, 530f); close()
        }
        canvas.drawPath(bolt, paint)
        return bitmap
    }
}
