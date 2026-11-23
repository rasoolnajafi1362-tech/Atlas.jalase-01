package com.example.service

import android.Manifest
import android.app.Application
import android.media.MediaFormat
import android.media.MediaRecorder
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowMediaExtractor
import org.robolectric.shadows.ShadowMediaRecorder
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [RecordingMediaRecorderShadow::class])
class AudioRecordManagerTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = AudioRecordManager(app)

    @Before
    fun grantMicrophonePermission() {
        RecordingMediaRecorderShadow.resetRecordingShadow()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
    }

    @Test
    fun microphoneStartFailureDoesNotPublishAFileOrRecordingState() {
        RecordingMediaRecorderShadow.failStart = true
        assertNull(manager.startRecording(nowMillis = 1000))
        assertFalse(manager.isRecording)
        assertEquals(1, RecordingMediaRecorderShadow.releaseCalls)
        assertTrue(manager.getRecordingsDir().listFiles().orEmpty().isEmpty())
    }

    @Test
    fun missingPermissionDoesNotStartTheRecorderOrCreateAFile() {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)

        assertNull(manager.startRecording())
        assertFalse(manager.isRecording)
        assertEquals(0, RecordingMediaRecorderShadow.startCalls)
        assertFalse(File(app.filesDir, "meeting_recordings").exists())
    }

    @Test
    fun invalidOrEmptyOutputIsNotPublishedOrRewritten() {
        RecordingMediaRecorderShadow.outputBytes = byteArrayOf(0, 0, 0, 32, 102, 116, 121, 112)
        val invalidFile = requireNotNull(manager.startRecording(nowMillis = 1000))

        assertEquals(null to 0L, manager.stopRecording(nowMillis = 3000))
        assertArrayEquals(RecordingMediaRecorderShadow.outputBytes, invalidFile.readBytes())
        assertFalse(manager.isRecording)

        RecordingMediaRecorderShadow.outputBytes = byteArrayOf()
        val emptyFile = requireNotNull(manager.startRecording(nowMillis = 4000))
        assertEquals(null to 0L, manager.stopRecording(nowMillis = 6000))
        assertEquals(0L, emptyFile.length())
    }

    @Test
    fun stopFailureClearsStateAndDoesNotReturnTheFailedRecording() {
        val file = requireNotNull(manager.startRecording(nowMillis = 1000))
        file.writeText("partially captured audio")
        RecordingMediaRecorderShadow.failStop = true

        assertEquals(null to 0L, manager.stopRecording(nowMillis = 3000))
        assertFalse(manager.isRecording)
        assertEquals(1, RecordingMediaRecorderShadow.releaseCalls)
        assertEquals("partially captured audio", file.readText())
        assertEquals(null to 0L, manager.stopRecording())
    }

    @Test
    fun successfulStopReturnsTheRealOutputOnceAndAllowsRetry() {
        val previous = File(manager.getRecordingsDir(), "meeting_audio_1000.m4a")
        previous.writeText("previous recording")
        val file = requireNotNull(manager.startRecording(nowMillis = 1000))
        assertTrue(manager.isRecording)
        assertEquals(file, manager.startRecording(nowMillis = 1000))
        assertEquals(1, RecordingMediaRecorderShadow.startCalls)

        assertEquals(file to 2L, manager.stopRecording(nowMillis = 3000))
        assertFalse(manager.isRecording)
        assertTrue(file.isFile)
        assertEquals("previous recording", previous.readText())
        assertEquals(null to 0L, manager.stopRecording())

        val next = requireNotNull(manager.startRecording(nowMillis = 1000))
        assertNotEquals(file, next)
        assertEquals(next to 2L, manager.stopRecording(nowMillis = 3000))
    }
}

/** Replaces the Android microphone boundary with a controllable recorder and extractor track. */
@Implements(MediaRecorder::class)
class RecordingMediaRecorderShadow : ShadowMediaRecorder() {
    private lateinit var outputPath: String

    @Implementation
    override fun setOutputFile(path: String) {
        super.setOutputFile(path)
        outputPath = path
    }

    @Implementation
    override fun start() {
        startCalls++
        if (failStart) error("Microphone unavailable")
        super.start()
    }

    @Implementation
    override fun stop() {
        if (failStop) error("Recorder could not finalize")
        super.stop()
        val bytes = outputBytes ?: byteArrayOf(1, 2, 3)
        File(outputPath).writeBytes(bytes)
        if (outputBytes == null) {
            val format = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 1)
            ShadowMediaExtractor.addTrack(DataSource.toDataSource(outputPath), format, bytes)
        }
    }

    @Implementation
    override fun release() {
        releaseCalls++
        super.release()
    }

    companion object {
        var failStart = false
        var failStop = false
        var startCalls = 0
        var releaseCalls = 0
        var outputBytes: ByteArray? = null

        fun resetRecordingShadow() {
            failStart = false
            failStop = false
            startCalls = 0
            releaseCalls = 0
            outputBytes = null
        }
    }
}
