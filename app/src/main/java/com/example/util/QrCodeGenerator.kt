package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.MeetingSession
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import org.json.JSONObject

object QrCodeGenerator {

    fun generateQrBitmap(
        content: String,
        sizePx: Int = 512,
        darkColor: Int = Color.BLACK,
        lightColor: Int = Color.WHITE
    ): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints
            )
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) darkColor else lightColor)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decodes a QR Code Bitmap back into its text payload using ZXing MultiFormatReader.
     */
    fun decodeQrBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader().apply {
                val hints = mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.CHARACTER_SET to "UTF-8"
                )
                setHints(hints)
            }
            reader.decodeWithState(binaryBitmap).text
        } catch (e: Exception) {
            null
        }
    }

    fun createMemberCheckInPayload(
        sessionId: String,
        memberId: String,
        name: String,
        title: String
    ): String {
        val json = JSONObject()
        json.put("app", "ATLAS_MEETING_OS")
        json.put("type", "MEMBER_CHECKIN")
        json.put("sessionId", sessionId)
        json.put("memberId", memberId)
        json.put("name", name)
        json.put("title", title)
        json.put("timestamp", System.currentTimeMillis())
        return json.toString()
    }

    fun createSessionCheckInPayload(session: MeetingSession): String {
        val json = JSONObject()
        json.put("app", "ATLAS_MEETING_OS")
        json.put("type", "SESSION_CHECKIN")
        json.put("sessionId", session.id)
        json.put("title", session.title.ifBlank { "جلسه راهبردی سازمانی" })
        json.put("date", session.date)
        json.put("startTime", session.startTime)
        val membersArr = JSONArray()
        session.members.forEach { m ->
            val mObj = JSONObject()
            mObj.put("id", m.id)
            mObj.put("name", m.name)
            membersArr.put(mObj)
        }
        json.put("members", membersArr)
        return json.toString()
    }
}
