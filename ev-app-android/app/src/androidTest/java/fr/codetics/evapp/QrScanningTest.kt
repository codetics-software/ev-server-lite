// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import fr.codetics.evapp.app.PortalLink
import fr.codetics.evapp.app.QrCodeParser
import fr.codetics.evapp.features.badges.qrBitmap
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QrScanningTest {
    @Test
    fun mlKitDecodesAChargingPointCode() {
        val evse = "5f9f1c1e-7c3a-4d3a-9c55-0a7e6b1d2f10"
        val link = "https://supervision.reseau.fr/charge/reseau/$evse"
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val codes = Tasks.await(scanner.process(InputImage.fromBitmap(qrBitmap(link, 512), 0)))
        assertEquals(link, codes.single().rawValue)
        assertEquals(
            QrCodeParser.Outcome.Success(PortalLink("reseau", UUID.fromString(evse))),
            QrCodeParser.parse(codes.single().rawValue!!, "https://supervision.reseau.fr"),
        )
    }
}
