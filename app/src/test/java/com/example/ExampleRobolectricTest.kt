package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.viewmodel.*
import com.example.util.*
import com.example.service.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches atlas app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("اطلس جلسه ۰۱", appName)
    }

    @Test
    fun `test predefined cold chain scenario load`() {
        val scenario = PredefinedScenarios.getScenario("board_cold_chain")
        assertTrue(scenario.title.contains("زنجیره سرد"))
        assertEquals("external_supplier", scenario.scope)
        assertEquals(120, scenario.durationMins)
        assertTrue(scenario.opsData.enabled)
        assertEquals("96.4% تحویل موفق", scenario.opsData.fleetEff)
        assertEquals(4, scenario.members.size)
    }

    @Test
    fun `test predefined hr interview scenario load`() {
        val scenario = PredefinedScenarios.getScenario("hr_interview")
        assertTrue(scenario.title.contains("سرپرست عملیات انبار"))
        assertEquals("interview_hr", scenario.scope)
        assertTrue(scenario.hrData.enabled)
        assertEquals("مهندس آرش کاظمی", scenario.hrData.candidate)
        assertEquals("91 از 100", scenario.hrData.score)
    }

    @Test
    fun `test meeting repository save and archive`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MeetingRepository(context)

        val testSession = MeetingSession(
            title = "جلسه تست واحد لجستیک",
            durationMins = 90,
            notes = listOf(
                MeetingNote(
                    speaker = "سخنران",
                    text = "مصوب شد ناوگان مجهز به سنسور دما گردد.",
                    type = "🚨 مصوبه رسمی",
                    due = "تا شنبه"
                )
            )
        )

        repo.saveActiveSession(testSession)
        assertEquals("جلسه تست واحد لجستیک", repo.activeSession.value.title)
        assertEquals(1, repo.activeSession.value.notes.size)

        repo.archiveSession(testSession)
        assertTrue(repo.archiveList.value.any { it.title == "جلسه تست واحد لجستیک" })

        // Test export & import JSON
        val exported = repo.exportFullDatabaseJson()
        assertTrue(exported.contains("جلسه تست واحد لجستیک"))

        val importSuccess = repo.importFullDatabaseJson(exported)
        assertTrue(importSuccess)
    }

    @Test
    fun `test qr code generator creates valid bitmap`() {
        val payload = com.example.util.QrCodeGenerator.createMemberCheckInPayload(
            sessionId = "SES-1234",
            memberId = "MEM-01",
            name = "سخنران",
            title = "مدیر عملیات"
        )
        assertTrue(payload.contains("ATLAS_MEETING_OS"))
        assertTrue(payload.contains("سخنران"))

        val bitmap = com.example.util.QrCodeGenerator.generateQrBitmap(payload, 256)
        assertNotNull(bitmap)
        assertEquals(256, bitmap?.width)
        assertEquals(256, bitmap?.height)
    }

    @Test
    fun `test qr check in attendee marks present in viewmodel`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.viewmodel.MeetingViewModel(app)

        val targetMember = vm.activeSession.value.members.first()
        // set absent
        vm.updateMember(0, targetMember.copy(isPresent = false))
        assertFalse(vm.activeSession.value.members.first().isPresent)

        val qrPayload = com.example.util.QrCodeGenerator.createMemberCheckInPayload(
            sessionId = vm.activeSession.value.id,
            memberId = targetMember.id,
            name = targetMember.name,
            title = targetMember.title
        )

        val (success, msg) = vm.checkInAttendeeByQr(qrPayload)
        assertTrue(success)
        assertTrue(msg.contains(targetMember.name))
        assertTrue(vm.activeSession.value.members.first().isPresent)
        // Check that checkin note is added
        assertTrue(vm.activeSession.value.notes.any { it.text.contains(targetMember.name) })
    }

    @Test
    fun `test session qr code encode decode with zxing marks all members present`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.viewmodel.MeetingViewModel(app)

        // Set all members absent first
        vm.activeSession.value.members.forEachIndexed { idx, m ->
            vm.updateMember(idx, m.copy(isPresent = false))
        }
        assertTrue(vm.activeSession.value.members.none { it.isPresent })

        // Generate Session QR Payload in Setup tab format
        val sessionPayload = com.example.util.QrCodeGenerator.createSessionCheckInPayload(vm.activeSession.value)
        val qrBitmap = com.example.util.QrCodeGenerator.generateQrBitmap(sessionPayload, 300)
        assertNotNull(qrBitmap)

        // Decode bitmap using ZXing MultiFormatReader
        val decodedPayload = com.example.util.QrCodeGenerator.decodeQrBitmap(qrBitmap!!)
        assertEquals(sessionPayload, decodedPayload)

        // Scan decoded payload in Live tab
        val (ok, _) = vm.checkInAttendeeByQr(decodedPayload!!)
        assertTrue(ok)
        assertTrue(vm.activeSession.value.members.all { it.isPresent })
    }

    @Test
    fun `test google search grounded standard result added to meeting notes`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.viewmodel.MeetingViewModel(app)

        val groundedResult = com.example.service.GroundedSearchResult(
            query = "استاندارد GDP زنجیره سرد دارویی",
            summaryText = "طبق دستورالعمل WHO GDP، نگهداری داروهای حساس به دما باید در بازه ۲ تا ۸ درجه سانتی‌گراد با پایش مستمر سنسورهای کالیبره‌شده انجام شود.",
            sources = listOf(
                com.example.service.GroundedSource(
                    title = "WHO Good Distribution Practices",
                    uri = "https://www.who.int"
                )
            )
        )

        vm.addSearchResultToMeetingNotes(groundedResult)
        val latestNote = vm.activeSession.value.notes.first()
        assertTrue(latestNote.text.contains("استاندارد GDP زنجیره سرد دارویی"))
        assertTrue(latestNote.text.contains("WHO Good Distribution Practices"))
        assertEquals("مرجع استاندارد و حاکمیت شرکتی", latestNote.speaker)
    }

    @Test
    fun `test real-time transcription add and commit to meeting notes`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.viewmodel.MeetingViewModel(app)

        vm.setTranscribingSpeaker("سخنران")
        assertEquals("سخنران", vm.activeTranscribingSpeaker.value)

        // Add manual transcribed statement
        vm.addTranscriptionSegmentManually("مقرر گردید بودجه لجستیک 5PL به تصویب هیئت‌مدیره برسد.")
        assertEquals(1, vm.activeSession.value.transcriptions.size)

        val firstSegment = vm.activeSession.value.transcriptions.first()
        assertEquals("سخنران", firstSegment.speaker)
        assertTrue(firstSegment.text.contains("لجستیک 5PL"))
        assertFalse(firstSegment.isCommittedToNotes)

        // Commit segment to meeting note
        vm.commitTranscriptionSegmentToNote(firstSegment.id)
        assertTrue(vm.activeSession.value.transcriptions.first().isCommittedToNotes)

        // Verify note exists in session notes
        val createdNote = vm.activeSession.value.notes.last()
        assertEquals("سخنران", createdNote.speaker)
        assertTrue(createdNote.text.contains("لجستیک 5PL"))

        // Add second segment with another speaker
        vm.setTranscribingSpeaker("مدیرعامل")
        vm.addTranscriptionSegmentManually("حسابرسی رسمی با تایید بازرس قانونی انجام شود.")
        assertEquals(2, vm.activeSession.value.transcriptions.size)

        // Commit all remaining
        vm.commitAllTranscriptionsToNotes()
        assertTrue(vm.activeSession.value.transcriptions.all { it.isCommittedToNotes })

        // Export transcription text
        val exportedText = vm.exportTranscriptionAsFormattedText()
        assertTrue(exportedText.contains("متن پیاده‌سازی زنده مذاکرات صوتی"))
        assertTrue(exportedText.contains("سخنران"))
        assertTrue(exportedText.contains("مدیرعامل"))

        // Test clear
        vm.clearAllTranscriptions()
        assertEquals(0, vm.activeSession.value.transcriptions.size)
    }

    @Test
    fun `test transcription persistence in repository json`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MeetingRepository(context)

        val sessionWithTrans = MeetingSession(
            title = "جلسه بررسی حاکمیت شرکتی و گفت‌وگوهای زنده",
            transcriptions = listOf(
                com.example.data.TranscriptionSegment(
                    speaker = "سخنران",
                    text = "سامانه هوشمند پایش لجستیک تکمیل گردید.",
                    timeFormatted = "00:15:30"
                )
            )
        )

        repo.saveActiveSession(sessionWithTrans)
        val loaded = repo.activeSession.value
        assertEquals(1, loaded.transcriptions.size)
        assertEquals("سخنران", loaded.transcriptions.first().speaker)
        assertEquals("00:15:30", loaded.transcriptions.first().timeFormatted)

        val exportedJson = repo.exportFullDatabaseJson()
        assertTrue(exportedJson.contains("سامانه هوشمند پایش لجستیک تکمیل گردید."))

        val importedOk = repo.importFullDatabaseJson(exportedJson)
        assertTrue(importedOk)
    }

    @Test
    fun `test transcription AI analysis and action items extraction to notes`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.viewmodel.MeetingViewModel(app)

        // Seed transcriptions
        vm.addTranscriptionSegmentManually("سخنران: سنسورهای دمای کانتینرهای دارویی باید ظرف ۲۴ ساعت آینده نصب شوند.")
        vm.addTranscriptionSegmentManually("مدیرعامل: قرارداد شرکت حمل‌ونقل نهایی شود.")

        val testAnalysis = com.example.data.TranscriptionAiAnalysis(
            executiveSummary = "مذاکرات جلسه حول تسریع تجهیز ناوگان به سیستم پایش دما و نهایی‌سازی قراردادهای ۵PL متمرکز بود.",
            keyDecisions = listOf("تایید نصب فوری سنسورها", "تصویب قرارداد تامین"),
            actionItems = listOf(
                com.example.data.ExtractedActionItem(
                    title = "نصب سنسورهای دمای کانتینرهای دارویی",
                    assignee = "سخنران",
                    deadline = "ظرف ۲۴ ساعت",
                    priority = "فوری"
                ),
                com.example.data.ExtractedActionItem(
                    title = "نهایی‌سازی قرارداد شرکت حمل‌ونقل ۵PL",
                    assignee = "مدیرعامل",
                    deadline = "پایان هفته",
                    priority = "بالا"
                )
            )
        )

        // Save analysis to session
        vm.setTranscriptionAiAnalysis(testAnalysis)
        vm.updateAiSummary(testAnalysis.executiveSummary)
        assertEquals(testAnalysis.executiveSummary, vm.activeSession.value.aiSummary)

        // Test single action item to notes
        val firstAction = testAnalysis.actionItems.first()
        vm.addActionItemToMeetingNotes(firstAction)

        val latestNote = vm.activeSession.value.notes.last()
        assertEquals("سخنران", latestNote.speaker)
        assertEquals("نصب سنسورهای دمای کانتینرهای دارویی", latestNote.text)
        assertEquals("🚨 مصوبه رسمی", latestNote.type)
        assertEquals("ظرف ۲۴ ساعت", latestNote.due)

        // Test bulk add remaining
        vm.addAllActionItemsToMeetingNotes()
        assertTrue(vm.activeSession.value.notes.any { it.text.contains("قرارداد شرکت حمل‌ونقل ۵PL") })
    }

    @Test
    fun `test transcription AI analysis persistence in repository json`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MeetingRepository(context)

        val analysis = com.example.data.TranscriptionAiAnalysis(
            executiveSummary = "خلاصه تحلیلی هوش مصنوعی",
            keyDecisions = listOf("مصوبه ۱", "مصوبه ۲"),
            actionItems = listOf(
                com.example.data.ExtractedActionItem(
                    title = "بررسی گزارش زنجیره سرد",
                    assignee = "سخنران",
                    deadline = "فردا",
                    priority = "فوری",
                    isAddedToNotes = true
                )
            )
        )

        val session = MeetingSession(
            title = "جلسه تست تحلیل هوش مصنوعی",
            transcriptionAnalysis = analysis
        )

        repo.saveActiveSession(session)
        val loaded = repo.activeSession.value
        assertNotNull(loaded.transcriptionAnalysis)
        assertEquals("خلاصه تحلیلی هوش مصنوعی", loaded.transcriptionAnalysis?.executiveSummary)
        assertEquals(2, loaded.transcriptionAnalysis?.keyDecisions?.size)
        assertEquals(1, loaded.transcriptionAnalysis?.actionItems?.size)
        assertEquals("بررسی گزارش زنجیره سرد", loaded.transcriptionAnalysis?.actionItems?.first()?.title)
        assertTrue(loaded.transcriptionAnalysis?.actionItems?.first()?.isAddedToNotes == true)

        // Test Export / Import Database JSON
        val exported = repo.exportFullDatabaseJson()
        assertTrue(exported.contains("بررسی گزارش زنجیره سرد"))
        assertTrue(exported.contains("خلاصه تحلیلی هوش مصنوعی"))

        val ok = repo.importFullDatabaseJson(exported)
        assertTrue(ok)
    }

    @Test
    fun `test PDF report generation for stakeholders creates valid vector PDF`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val analysis = TranscriptionAiAnalysis(
            executiveSummary = "خلاصه مدیریتی راهبردی جلسه زنجیره سرد و لجستیک ۵PL دارویی.",
            keyDecisions = listOf(
                "تصویب انعقاد قرارداد با شرکت لجستیک سردسیر",
                "تعیین سقف بودجه ۲۰ میلیارد ریال برای سیستم مانیتورینگ آنلاین"
            ),
            actionItems = listOf(
                ExtractedActionItem(
                    title = "مذاکره نهایی تعرفه کانتینرهای زیر صفر",
                    assignee = "سخنران",
                    deadline = "ظرف ۴۸ ساعت",
                    priority = "فوری",
                    isAddedToNotes = true
                ),
                ExtractedActionItem(
                    title = "تنظیم قرارداد با واحد حقوقی",
                    assignee = "مشاور حقوقی",
                    deadline = "پایان هفته",
                    priority = "بالا",
                    isAddedToNotes = false
                )
            )
        )

        val session = MeetingSession(
            title = "جلسه اضطراری لجستیک زنجیره سرد دارویی",
            date = "1405-08-20",
            startTime = "10:00",
            durationMins = 60,
            location = "سالن کنفرانس سپهر",
            scope = "5PL Logistics",
            extCompany = "داروسازی سینا",
            extRef = "CT-5PL-992",
            members = listOf(
                Member(name = "سخنران", title = "مدیر لجستیک و عملیات", unit = "عملیات ۵PL", isPresent = true),
                Member(name = "دکتر جلالی", title = "مدیرعامل", unit = "هیئت‌مدیره", isPresent = true),
                Member(name = "مهندس سعیدی", title = "مدیر انبارش", unit = "زنجیره سرد", isPresent = false)
            ),
            notes = listOf(
                MeetingNote(
                    speaker = "سخنران",
                    text = "ارائه شاخص‌های بهره‌وری ناوگان سردخانه‌ای و کاهش اتلاف دما به زیر ۲ درصد.",
                    type = "📊 تصمیم",
                    due = "فوری",
                    isDone = true
                ),
                MeetingNote(
                    speaker = "دکتر جلالی",
                    text = "موافقت با الحاقیه سنسورهای IoT شرکت فناوران پایش.",
                    type = "🚨 مصوبه رسمی",
                    due = "۲۴ ساعت",
                    isDone = false
                )
            ),
            opsData = OpsData(
                enabled = true,
                fleetEff = "94.5%",
                coldChain = "2°C to 8°C Verified",
                palletModel = "EUR-Pallet / Racking High-Density",
                budget = "20,000,000,000 ریال",
                summary = "ناوگان آماده تخصیص است."
            ),
            hrData = HrData(
                enabled = true,
                candidate = "مهندس رضایی",
                position = "سرپرست برج کنترل",
                score = "91 / 100",
                decision = "تأیید استخدام با دوره آزمایشی",
                notes = "دارای مدرک معتبر مدیریت زنجیره تأمین."
            ),
            transcriptionAnalysis = analysis,
            aiSummary = analysis.executiveSummary
        )

        val pdfFile = PdfReportGenerator.generatePdf(context, session)
        assertNotNull(pdfFile)
        assertTrue("PDF file should exist on disk", pdfFile.exists())
        assertTrue("PDF file size should be greater than zero", pdfFile.length() > 0)

        // Verify PDF Header Magic Bytes (%PDF-)
        val headerBytes = ByteArray(5)
        pdfFile.inputStream().use { stream ->
            val readCount = stream.read(headerBytes)
            assertEquals(5, readCount)
        }
        val headerString = String(headerBytes)
        assertEquals("%PDF-", headerString)

        // Verify Intent creation
        val shareIntent = PdfReportGenerator.createSharePdfIntent(context, pdfFile, session.title)
        assertEquals(android.content.Intent.ACTION_SEND, shareIntent.action)
        assertEquals("application/pdf", shareIntent.type)
        assertNotNull(shareIntent.getParcelableExtra(android.content.Intent.EXTRA_STREAM))

        val viewIntent = PdfReportGenerator.createViewPdfIntent(context, pdfFile)
        assertEquals(android.content.Intent.ACTION_VIEW, viewIntent.action)
        assertEquals("application/pdf", viewIntent.type)
    }

    @Test
    fun `test ViewModel generateMeetingPdf integration`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MeetingViewModel(app)

        vm.updateTitle("جلسه پایش عملکرد فصلی")
        vm.addNote("سخنران", "تعهد تحویل سریع مرسولات دارویی", "🚨 مصوبه رسمی", "فردا")

        val generatedFile = vm.generateMeetingPdf(app)
        assertNotNull(generatedFile)
        assertTrue(generatedFile.exists())
        assertTrue(generatedFile.length() > 0)
        assertTrue(generatedFile.name.endsWith(".pdf"))
    }

    @Test
    fun `test AudioRecordManager recording lifecycle and amplitude tracking`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AudioRecordManager(context)

        assertFalse(manager.isRecording)
        val recordedFile = manager.startRecording()
        assertNotNull(recordedFile)
        assertTrue(manager.isRecording)

        // Amplitude tracking should be non-negative
        val amp = manager.getMaxAmplitude()
        assertTrue(amp >= 0)
        val norm = manager.getNormalizedAmplitude()
        assertTrue(norm in 0f..1f)

        // Stop recording
        val (finalFile, duration) = manager.stopRecording()
        assertFalse(manager.isRecording)
        assertNotNull(finalFile)
        assertTrue(finalFile!!.exists())
        assertTrue(duration >= 0)

        // Directory listing
        val recordings = manager.listRecordedAudioFiles()
        assertTrue(recordings.any { it.name == finalFile.name })

        // Clean up
        val deleted = manager.deleteRecording(finalFile)
        assertTrue(deleted)
    }

    @Test
    fun `test ViewModel audio analysis integration and session update`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MeetingViewModel(app)

        val testAnalysis = AudioMeetingAnalysis(
            fullTranscription = "سخنران: بررسی زنجیره سرد و تخصیص ناوگان ۵PL با دمای منفی ۱۸ درجه.",
            executiveSummary = "خلاصه اجرایی مباحث صوتی: تصویب تخصیص ناوگان و ارتقای مانیتورینگ آنلاین.",
            keyDecisions = listOf("تصویب سقف بودجه ۲۰ میلیارد ریال"),
            actionItems = listOf(
                ExtractedActionItem(
                    title = "انعقاد قرارداد سنسورهای هوشمند",
                    assignee = "سخنران",
                    deadline = "ظرف ۴۸ ساعت",
                    priority = "فوری"
                )
            ),
            audioDurationSec = 120,
            audioFileName = "meeting_audio_test.m4a"
        )

        vm.applyAudioAnalysisToSession(testAnalysis)

        val updatedSession = vm.activeSession.value
        assertEquals(testAnalysis.executiveSummary, updatedSession.aiSummary)
        assertNotNull(updatedSession.latestAudioAnalysis)
        assertEquals("meeting_audio_test.m4a", updatedSession.latestAudioAnalysis?.audioFileName)
        assertTrue(updatedSession.transcriptions.any { it.text.contains("بررسی زنجیره سرد") })
        assertEquals(1, updatedSession.transcriptionAnalysis?.actionItems?.size)
        assertEquals("سخنران", updatedSession.transcriptionAnalysis?.actionItems?.first()?.assignee)
    }

    @Test
    fun `test GeminiAiService audio validation for empty and non-existent files`() {
        kotlinx.coroutines.runBlocking {
            val service = GeminiAiService()
            val dummySession = MeetingSession(title = "جلسه تست صوت")

            // 1. Non-existent file
            val nonExistentFile = java.io.File("/tmp/non_existent_audio.m4a")
            val failResult = service.transcribeAndSummarizeAudio(nonExistentFile, dummySession, "AIzaFakeKey")
            assertTrue(failResult.isFailure)
            assertTrue(failResult.exceptionOrNull()?.message?.contains("یافت نشد") == true)

            // 2. Missing API key
            val tempFile = java.io.File.createTempFile("test_audio", ".m4a")
            tempFile.writeBytes(byteArrayOf(0x01, 0x02, 0x03))
            val noKeyResult = service.transcribeAndSummarizeAudio(tempFile, dummySession, "")
            assertTrue(noKeyResult.isFailure)
            assertTrue(noKeyResult.exceptionOrNull()?.message?.contains("Gemini API Key") == true)
            tempFile.delete()
        }
    }

    @Test
    fun `test Room AttendeeEntity, AttendeeDao, and AttendeeRepository CRUD operations`() {
        kotlinx.coroutines.runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = com.example.data.local.AtlasDatabase.createInMemory(context)
            val dao = db.attendeeDao()
            val repo = com.example.data.local.AttendeeRepository(dao)

            val sessionId = "ATL-ROOM-TEST-01"
            val attendee1 = com.example.data.local.AttendeeEntity(
                id = "att-1",
                sessionId = sessionId,
                name = "سخنران",
                role = "مدیر زنجیره تأمین و عملیات",
                department = "مدیریت ارشد",
                isPresent = true,
                attendanceStatus = com.example.data.local.AttendeeEntity.STATUS_PRESENT,
                checkInTime = "10:00:00",
                createdAt = 1000L
            )
            val attendee2 = com.example.data.local.AttendeeEntity(
                id = "att-2",
                sessionId = sessionId,
                name = "سارا کاظمی",
                role = "تضمین کیفیت",
                department = "QA",
                isPresent = false,
                attendanceStatus = com.example.data.local.AttendeeEntity.STATUS_ABSENT,
                createdAt = 2000L
            )

            // 1. Insert attendees
            repo.upsertAll(listOf(attendee1, attendee2))
            val initialList = repo.getAttendeesForSessionOnce(sessionId)
            assertEquals(2, initialList.size)
            assertEquals("سخنران", initialList[0].name)
            assertEquals("مدیر زنجیره تأمین و عملیات", initialList[0].role)
            assertTrue(initialList[0].isPresent)
            assertFalse(initialList[1].isPresent)

            // 2. Update attendance status (mark attendee2 as Late / Present)
            repo.updateAttendanceStatus(
                id = "att-2",
                isPresent = true,
                status = com.example.data.local.AttendeeEntity.STATUS_LATE,
                checkInTime = "10:15:00"
            )
            val updatedAtt2 = dao.getAttendeeById("att-2")
            assertNotNull(updatedAtt2)
            assertTrue(updatedAtt2!!.isPresent)
            assertEquals(com.example.data.local.AttendeeEntity.STATUS_LATE, updatedAtt2.attendanceStatus)
            assertEquals("10:15:00", updatedAtt2.checkInTime)

            // 3. Update role
            repo.updateRole("att-2", "مدیر ارشد تضمین کیفیت (QA)")
            val updatedRoleAtt2 = dao.getAttendeeById("att-2")
            assertEquals("مدیر ارشد تضمین کیفیت (QA)", updatedRoleAtt2?.role)

            // 4. Delete attendee
            repo.deleteById("att-1")
            val remaining = repo.getAttendeesForSessionOnce(sessionId)
            assertEquals(1, remaining.size)
            assertEquals("att-2", remaining.first().id)

            db.close()
        }
    }

    @Test
    fun `test ViewModel Room attendee management and session synchronization`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MeetingViewModel(app)

        val initialCount = vm.activeSession.value.members.size
        vm.addAttendeeWithDetails(
            name = "دکتر نیما شریفی",
            role = "مشاور حاکمیت شرکتی",
            department = "هیئت‌مدیره",
            isPresent = true
        )

        val afterAddMembers = vm.activeSession.value.members
        assertEquals(initialCount + 1, afterAddMembers.size)
        val addedMember = afterAddMembers.last()
        assertEquals("دکتر نیما شریفی", addedMember.name)
        assertEquals("مشاور حاکمیت شرکتی", addedMember.title)
        assertEquals("هیئت‌مدیره", addedMember.unit)
        assertTrue(addedMember.isPresent)

        // Toggle presence via AttendeeEntity
        val entity = com.example.data.local.AttendeeEntity.fromMember(
            member = addedMember,
            sessionId = vm.activeSession.value.id
        )
        vm.changeAttendeeStatus(entity, com.example.data.local.AttendeeEntity.STATUS_EXCUSED, false)

        val updatedMember = vm.activeSession.value.members.find { it.id == addedMember.id }
        assertNotNull(updatedMember)
        assertFalse(updatedMember!!.isPresent)

        // Delete by ID
        vm.deleteAttendeeById(addedMember.id)
        assertFalse(vm.activeSession.value.members.any { it.id == addedMember.id })
    }

    @Test
    fun `test BoardAnalyticsEngine computes attendance trends and action completion over time`() {
        val session = PredefinedScenarios.getScenario("board_cold_chain")
        val roomAttendees = session.members.mapIndexed { idx, m ->
            com.example.data.local.AttendeeEntity.fromMember(m, session.id, idx)
        }
        val archivedSession = PredefinedScenarios.getScenario("supplier_contract")

        val analytics = com.example.ui.components.BoardAnalyticsEngine.computeAnalytics(
            activeSession = session,
            roomAttendees = roomAttendees,
            archiveList = listOf(archivedSession),
            selectedBoardMemberName = null
        )

        // Should have multi-period trend points ending with current session
        assertTrue(analytics.trendSeries.size >= 5)
        val latestPoint = analytics.trendSeries.last()
        assertEquals(session.id, latestPoint.sessionId)
        assertTrue(latestPoint.attendanceRatePercent in 0..100)
        assertTrue(latestPoint.completionRatePercent in 0..100)

        // Board members performance list should match roomAttendees
        assertEquals(roomAttendees.size, analytics.boardMembers.size)
        assertTrue(analytics.boardMembers.any { it.name.contains("سخنران") })

        // Filter by specific board member
        val filteredAnalytics = com.example.ui.components.BoardAnalyticsEngine.computeAnalytics(
            activeSession = session,
            roomAttendees = roomAttendees,
            archiveList = listOf(archivedSession),
            selectedBoardMemberName = "سخنران"
        )
        assertEquals(1, filteredAnalytics.boardMembers.size)
        assertEquals("سخنران", filteredAnalytics.boardMembers.first().name)
    }

    @Test
    fun `test Room Full-Text Search FTS4 across title, resolutions, and executive summary with combined filters`() {
        kotlinx.coroutines.runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = com.example.data.local.AtlasDatabase.createInMemory(context)
            val minutesDao = db.meetingMinutesDao()
            val searchRepo = com.example.data.local.MinutesSearchRepository(minutesDao)

            val coldChainSession = PredefinedScenarios.getScenario("board_cold_chain")
            val hrSession = PredefinedScenarios.getScenario("hr_interview")
            val contractSession = PredefinedScenarios.getScenario("supplier_contract")

            // Sync all 3 sessions into Room FTS index
            searchRepo.syncSessionsToMinutesIndex(
                activeSession = coldChainSession,
                archiveList = listOf(hrSession, contractSession),
                seedPredefinedScenariosIfArchiveEmpty = false
            )

            // 1. Search by keyword in Title & Notes ("زنجیره سرد")
            val coldChainHits = searchRepo.searchMinutesOnce(rawQuery = "زنجیره سرد")
            assertTrue("Should find cold chain session via FTS", coldChainHits.isNotEmpty())
            val firstHit = coldChainHits.first()
            assertEquals(coldChainSession.id, firstHit.entity.sessionId)
            assertTrue(firstHit.matchedSnippet.contains("زنجیره سرد"))
            assertTrue(firstHit.totalOccurrences > 0)

            // 2. Search by keyword in Resolutions/Notes ("پالت")
            val palletHits = searchRepo.searchMinutesOnce(rawQuery = "پالت")
            assertTrue("Should find pallet resolution in supplier contract", palletHits.any { it.entity.sessionId == contractSession.id })

            // 3. Test Combined Filters: Scope + Responsible Member + Status
            val hrFilteredHits = searchRepo.searchMinutesOnce(
                rawQuery = "",
                scopeFilter = "interview_hr",
                statusFilter = "ALL",
                memberFilter = "ALL"
            )
            assertEquals(1, hrFilteredHits.size)
            assertEquals(hrSession.id, hrFilteredHits.first().entity.sessionId)

            // 4. Test Direct Session Restoration from Room FTS Entity
            val restoredSession = firstHit.entity.toMeetingSession()
            assertNotNull(restoredSession)
            assertEquals(coldChainSession.title, restoredSession?.title)

            // 5. Verify keyword highlighting AnnotatedString helper
            val highlighted = com.example.ui.components.buildHighlightedAnnotatedString(
                text = firstHit.matchedSnippet,
                query = "زنجیره سرد",
                baseColor = androidx.compose.ui.graphics.Color.Gray,
                highlightBackground = androidx.compose.ui.graphics.Color.Cyan,
                highlightColor = androidx.compose.ui.graphics.Color.White
            )
            assertTrue(highlighted.text.contains("زنجیره سرد"))
            assertTrue(highlighted.spanStyles.size >= 2)

            db.close()
        }
    }

    @Test
    fun `test voice recording FAB and bottom sheet elapsed time and waveform sample calculations`() {
        assertEquals("00:02:05", com.example.ui.components.formatRecordingElapsedTime(125L, includeHours = true))
        assertEquals("02:05", com.example.ui.components.formatRecordingElapsedTime(125L, includeHours = false))
        assertEquals("01:01:01", com.example.ui.components.formatRecordingElapsedTime(3661L, includeHours = false))

        val idleSample = com.example.ui.components.computeEffectiveWaveSample(
            isCapturing = false,
            recordingAmplitude = 0.8f,
            audioRmsLevel = 5f,
            tickIndex = 10
        )
        assertEquals(0.08f, idleSample, 0.001f)

        val activeSample = com.example.ui.components.computeEffectiveWaveSample(
            isCapturing = true,
            recordingAmplitude = 0.72f,
            audioRmsLevel = 4f,
            tickIndex = 10
        )
        assertTrue(activeSample in 0.70f..1.0f)
    }

    @Test
    fun `test microphone input sensitivity slider and conference room noise gate filtering`() {
        // Low sensitivity (0.35f) for large noisy conference rooms should yield a higher noise gate threshold
        val highGate = com.example.service.AudioRecordManager.computeNoiseGateThreshold(0.35f)
        // High sensitivity (0.95f) for distant speakers should yield a much lower noise gate threshold
        val lowGate = com.example.service.AudioRecordManager.computeNoiseGateThreshold(0.95f)
        assertTrue("Lower sensitivity must produce a higher noise gate cutoff", highGate > lowGate)

        // Ambient HVAC / background noise below gate threshold (e.g. 0.15f when gate is ~0.27f) must be suppressed to 0f
        val gatedBackgroundNoise = com.example.service.AudioRecordManager.applySensitivityAndNoiseGate(
            rawNormalized = 0.15f,
            sensitivity = 0.35f
        )
        assertEquals(0f, gatedBackgroundNoise, 0.0001f)

        // Clear board speaker voice above threshold (e.g. 0.65f) must pass through and scale smoothly
        val activeSpeechSignal = com.example.service.AudioRecordManager.applySensitivityAndNoiseGate(
            rawNormalized = 0.65f,
            sensitivity = 0.70f
        )
        assertTrue("Active speech above noise gate should pass through", activeSpeechSignal > 0.30f)

        // Verify ViewModel state update and clamping
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.viewmodel.MeetingViewModel(app)
        assertEquals(0.70f, vm.micInputSensitivity.value, 0.001f)

        vm.updateMicInputSensitivity(0.35f)
        assertEquals(0.35f, vm.micInputSensitivity.value, 0.001f)

        // Clamping check
        vm.updateMicInputSensitivity(0.01f)
        assertEquals(com.example.service.AudioRecordManager.MIN_SENSITIVITY, vm.micInputSensitivity.value, 0.001f)
    }

    @Test
    fun `test 5-second silence detection auto-pauses recording and resumes when speech is detected`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = com.example.service.AudioRecordManager(context)

        val t0 = 10_000L
        val outFile = manager.startRecording(nowMillis = t0)
        assertNotNull(outFile)
        assertTrue(manager.isRecording)
        assertFalse(manager.isAutoPausedBySilence)

        // 1. Active speech at t = 13s (3 seconds of active speech)
        val speechStatus = manager.evaluateSilenceDetection(
            gatedNormalizedAmplitude = 0.55f,
            nowMillis = t0 + 3_000L
        )
        assertFalse(speechStatus.isAutoPaused)
        assertEquals(0L, speechStatus.currentSilenceMs)
        assertEquals(3L, manager.getElapsedDurationSec(t0 + 3_000L))

        // 2. Silence for 4.0 seconds (t = 17s) -> below 5s threshold, still recording
        val preThresholdSilence = manager.evaluateSilenceDetection(
            gatedNormalizedAmplitude = 0.0f,
            nowMillis = t0 + 7_000L
        )
        assertFalse("Should not auto-pause before 5 seconds of silence", preThresholdSilence.isAutoPaused)
        assertEquals(4_000L, preThresholdSilence.currentSilenceMs)
        assertEquals(0.8f, preThresholdSilence.silenceCountdownProgress, 0.01f)

        // 3. Silence reaches 5.0 seconds (t = 18s) -> MUST automatically pause recording!
        val autoPausedStatus = manager.evaluateSilenceDetection(
            gatedNormalizedAmplitude = 0.0f,
            nowMillis = t0 + 8_000L
        )
        assertTrue("Recording must auto-pause after 5 seconds of silence", autoPausedStatus.isAutoPaused)
        assertTrue(manager.isAutoPausedBySilence)
        assertEquals(1, autoPausedStatus.autoPauseEventsCount)
        val activeSecAtPause = manager.getElapsedDurationSec(t0 + 8_000L)
        assertEquals(8L, activeSecAtPause)

        // 4. Silence continues for 6 more seconds (t = 24s) -> active duration must stay frozen at 8s
        val prolongedSilenceStatus = manager.evaluateSilenceDetection(
            gatedNormalizedAmplitude = 0.0f,
            nowMillis = t0 + 14_000L
        )
        assertTrue(prolongedSilenceStatus.isAutoPaused)
        assertEquals("Active duration should freeze while auto-paused on silence", 8L, manager.getElapsedDurationSec(t0 + 14_000L))
        assertEquals("Should track 6 seconds of trimmed silence", 6L, prolongedSilenceStatus.totalSilenceTrimmedSec)

        // 5. Speech is detected at t = 24s -> MUST automatically resume recording!
        val resumedStatus = manager.evaluateSilenceDetection(
            gatedNormalizedAmplitude = 0.68f,
            nowMillis = t0 + 14_000L
        )
        assertFalse("Recording must automatically resume as soon as speech is detected", resumedStatus.isAutoPaused)
        assertFalse(manager.isAutoPausedBySilence)
        assertEquals(1, resumedStatus.autoResumeEventsCount)
        assertEquals(6L, resumedStatus.totalSilenceTrimmedSec)

        // 6. Speak for 2 more seconds (t = 26s) and stop -> total active speech duration = 8 + 2 = 10s (6s of silence excluded)
        val (_, finalActiveDurationSec) = manager.stopRecording(nowMillis = t0 + 16_000L)
        assertEquals(10L, finalActiveDurationSec)
    }

    @Test
    fun `test live transcription waveform overlay quality assessment and sample speech verification`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.viewmodel.MeetingViewModel(app)

        // 1. Standby quality evaluation when not capturing
        val standbyQuality = com.example.ui.components.evaluateRecordingQuality(
            isCapturing = false,
            isAutoPausedBySilence = false,
            effectiveSignal = 0f,
            micSensitivity = 0.70f,
            interimTranscript = "",
            recentTranscriptions = emptyList()
        )
        assertEquals(0, standbyQuality.clarityScorePercent)
        assertFalse(standbyQuality.isOptimalQuality)

        // 2. Simulate live speech transcription sample in ViewModel
        vm.simulateLiveTranscriptionOverlaySample()
        val liveInterim = vm.interimTranscript.value
        assertTrue("Interim transcript must be populated for overlay", liveInterim.isNotBlank())
        assertTrue(vm.activeSession.value.transcriptions.isNotEmpty())

        // 3. Evaluate HD Speech recording quality with active signal and live transcription text
        val activeQuality = com.example.ui.components.evaluateRecordingQuality(
            isCapturing = true,
            isAutoPausedBySilence = false,
            effectiveSignal = 0.65f,
            micSensitivity = 0.70f,
            interimTranscript = liveInterim,
            recentTranscriptions = vm.activeSession.value.transcriptions
        )
        assertTrue("Clarity score should be high during clear speech", activeQuality.clarityScorePercent >= 80)
        assertTrue("SNR estimate should be positive dB", activeQuality.snrEstimateDb >= 18)
        assertTrue(activeQuality.isOptimalQuality)
        assertTrue(activeQuality.recognizedWordCount > 5)
    }

    @Test
    fun `test resolution status type badge resolution`() {
        // 1. Completed note
        val completedNote = MeetingNote(
            speaker = "سخنران",
            text = "نصب سنسورهای دیتالاگر زنجیره سرد تکمیل شد.",
            isDone = true,
            due = "۱۴۰۵/۰۸/۱۰"
        )
        assertEquals(ResolutionStatusType.COMPLETED, completedNote.resolveStatus())

        // 2. Deferred note
        val deferredNote = MeetingNote(
            speaker = "مدیر تدارکات",
            text = "تأمین پالت‌های استاندارد به تعویق افتاد.",
            isDone = false,
            due = "معوق به علت تاخیر گمرکی"
        )
        assertEquals(ResolutionStatusType.DEFERRED, deferredNote.resolveStatus())

        // 3. In Progress note
        val inProgressNote = MeetingNote(
            speaker = "مدیر مالی",
            text = "بررسی بودجه ناوگان یخچال‌دار در جریان است.",
            isDone = false,
            due = "تا انتهای هفته"
        )
        assertEquals(ResolutionStatusType.IN_PROGRESS, inProgressNote.resolveStatus())
    }

    @Test
    fun `test smart notification reminder helper for action items`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val context: Context = app

        val note = MeetingNote(
            id = "NOTE-TEST-01",
            speaker = "سخنران",
            text = "بررسی سنسورهای دمای کانتینرهای دارویی 2 الی 8 درجه",
            due = "تا فردا ساعت ۱۲",
            type = "🚨 مصوبه رسمی",
            isDone = false
        )

        // Test sending single reminder
        val sent = MeetingNotificationHelper.sendActionItemReminder(
            context = context,
            note = note,
            sessionTitle = "جلسه اضطراری زنجیره سرد"
        )
        assertTrue("Notification helper should successfully post or construct reminder", sent)

        // Test sending batch reminders for unresolved items
        val batchNotes = listOf(
            note,
            MeetingNote(
                id = "NOTE-TEST-02",
                speaker = "مدیر کنترل کیفیت",
                text = "ارزیابی انطباق استاندارد WHO GDP",
                due = "معوق",
                isDone = false
            ),
            MeetingNote(
                id = "NOTE-TEST-03",
                speaker = "رئیس هیئت‌مدیره",
                text = "امضای نهایی قرارداد",
                isDone = true
            )
        )

        val batchSentCount = MeetingNotificationHelper.sendAllPendingReminders(
            context = context,
            notes = batchNotes,
            sessionTitle = "جلسه اضطراری زنجیره سرد"
        )
        assertEquals("Should dispatch notifications for the 2 pending/deferred action items", 2, batchSentCount)
    }

    @Test
    fun `test ai agenda suggestion generation and application in viewmodel`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MeetingViewModel(app)

        val geminiService = GeminiAiService()
        val unresolved = listOf(
            MeetingNote(
                speaker = "سخنران",
                text = "کالیبراسیون سنسورهای برودتی خودروهای حمل انسولین",
                due = "معوق",
                isDone = false
            )
        )
        val recurringObjectives = listOf(
            "توسعه زنجیره سرد دارویی",
            "انطباق با TTAC"
        )

        // Run agenda generator
        val result = geminiService.generateAgendaSuggestions(
            unresolvedNotes = unresolved,
            pastSessions = emptyList(),
            recurringObjectives = recurringObjectives,
            customApiKey = ""
        )
        assertTrue(result.isSuccess)
        val suggestions = result.getOrNull()
        assertNotNull(suggestions)
        assertTrue(suggestions!!.isNotEmpty())

        val topSuggestion = suggestions.first()
        assertTrue(topSuggestion.topicTitle.isNotBlank())
        assertTrue(topSuggestion.rationalReason.isNotBlank())

        // Test applying single topic into active session
        val initialNoteCount = vm.activeSession.value.notes.size
        vm.applySuggestedAgendaTopic(topSuggestion)
        assertEquals(initialNoteCount + 1, vm.activeSession.value.notes.size)
        assertTrue(vm.activeSession.value.notes.last().text.contains(topSuggestion.topicTitle))

        // Test applying all suggestions
        val sampleBatch = listOf(
            AgendaSuggestion(topicTitle = "موضوع اول", rationalReason = "دلیل اول"),
            AgendaSuggestion(topicTitle = "موضوع دوم", rationalReason = "دلیل دوم")
        )
        val beforeBatchCount = vm.activeSession.value.notes.size
        val updatedNotes = vm.activeSession.value.notes + sampleBatch.map { s ->
            MeetingNote(speaker = s.proposedSpeaker, text = "دستور جلسه: ${s.topicTitle}", type = s.suggestedActionType)
        }
        vm.updateAiSummary(vm.activeSession.value.aiSummary)
        assertEquals(beforeBatchCount + 2, updatedNotes.size)
    }

    @Test
    fun `test meeting health trend analysis and recurring blockers identification`() = kotlinx.coroutines.runBlocking {
        val geminiService = GeminiAiService()
        val testSession = PredefinedScenarios.getScenario("board_cold_chain")

        val result = geminiService.analyzeMeetingHealthTrends(
            sessions = listOf(testSession),
            customApiKey = ""
        )
        assertTrue(result.isSuccess)
        val report = result.getOrNull()
        assertNotNull(report)
        assertTrue("Health score should be between 0 and 100", report!!.healthScore in 0..100)
        assertTrue(report.gradeLabel.isNotBlank())
        assertTrue("Decision velocity should be evaluated", report.decisionVelocity.isNotBlank())
        assertTrue("Recurring blockers should be identified", report.recurringBlockers.isNotEmpty())
        assertTrue("Mitigation strategy must be provided for blockers", report.recurringBlockers.first().mitigationStrategy.isNotBlank())
        assertTrue("Efficiency recommendations must be provided", report.efficiencyRecommendations.isNotEmpty())
    }

    @Test
    fun `test advanced meeting list filter states and reset in viewmodel`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MeetingViewModel(app)

        vm.updateListFilterQuery("زنجیره")
        assertEquals("زنجیره", vm.listFilterQuery.value)

        vm.updateListFilterScope("external_supplier")
        assertEquals("external_supplier", vm.listFilterScope.value)

        vm.updateListFilterStatus("IN_PROGRESS")
        assertEquals("IN_PROGRESS", vm.listFilterStatus.value)

        vm.updateListFilterMember("سخنران")
        assertEquals("سخنران", vm.listFilterMember.value)

        // Reset
        vm.resetListFilters()
        assertEquals("", vm.listFilterQuery.value)
        assertEquals("ALL", vm.listFilterScope.value)
        assertEquals("ALL", vm.listFilterStatus.value)
        assertEquals("ALL", vm.listFilterMember.value)
    }

    @Test
    fun `test audio sentiment analysis and high tension segment detection with Gemini fallback`() = kotlinx.coroutines.runBlocking {
        val geminiService = GeminiAiService()
        val testSession = PredefinedScenarios.getScenario("board_cold_chain")

        val result = geminiService.analyzeAudioSentimentAndTension(
            audioFile = null,
            session = testSession,
            customApiKey = ""
        )
        assertTrue(result.isSuccess)
        val sentiment = result.getOrNull()
        assertNotNull(sentiment)
        assertTrue("Overall tone should not be blank", sentiment!!.overallTone.isNotBlank())
        assertTrue("Harmony score should be in 0..100", sentiment.harmonyScore in 0..100)
        assertTrue("Tension score should be in 0..100", sentiment.tensionScore in 0..100)
        assertTrue("Percentages sum should be approx 100", (sentiment.constructivePercentage + sentiment.neutralPercentage + sentiment.tensionPercentage) in 90..110)
        assertTrue("Dominant emotion should be set", sentiment.dominantEmotion.isNotBlank())
        assertTrue("Summary evaluation should be provided", sentiment.summaryEvaluation.isNotBlank())
        assertTrue("High tension segments should be flagged", sentiment.flaggedSegments.isNotEmpty())

        val firstSeg = sentiment.flaggedSegments.first()
        assertTrue("Tension level must be standard", firstSeg.tensionLevel in listOf("شدید", "بحرانی", "متوسط"))
        assertTrue("Speaker must be set", firstSeg.speaker.isNotBlank())
        assertTrue("Trigger topic must be set", firstSeg.triggerTopic.isNotBlank())
        assertTrue("Quote excerpt must be captured", firstSeg.quoteExcerpt.isNotBlank())
        assertTrue("Review recommendation must be provided", firstSeg.reviewRecommendation.isNotBlank())
        assertFalse("Initially should not be marked as reviewed", firstSeg.isReviewed)
    }

    @Test
    fun `test audio sentiment viewmodel integration, toggle reviewed, and add resolution to meeting notes`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MeetingViewModel(app)
        vm.loadScenario("board_cold_chain")

        // Trigger sentiment analysis
        vm.analyzeAudioSentimentWithGemini()
        for (i in 0 until 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (vm.audioSentimentAnalysis.value != null) break
            kotlinx.coroutines.delay(20)
        }
        val sentiment = vm.audioSentimentAnalysis.value
        assertNotNull(sentiment)
        assertTrue(sentiment!!.flaggedSegments.isNotEmpty())

        val targetSeg = sentiment.flaggedSegments.first()
        val segId = targetSeg.id

        // Toggle reviewed with notes
        vm.toggleFlaggedSegmentReviewed(segId, "توسط سخنران در جلسه بازبینی شد")
        val updatedSentiment = vm.audioSentimentAnalysis.value
        val updatedSeg = updatedSentiment?.flaggedSegments?.find { it.id == segId }
        assertNotNull(updatedSeg)
        assertTrue("Segment must now be marked as reviewed", updatedSeg!!.isReviewed)
        assertEquals("توسط سخنران در جلسه بازبینی شد", updatedSeg.reviewNotes)

        // Add resolution to meeting notes
        val secondSeg = sentiment.flaggedSegments.last()
        val beforeNoteCount = vm.activeSession.value.notes.size
        vm.applyFlaggedSegmentToMeetingNotes(secondSeg)

        val afterNotes = vm.activeSession.value.notes
        assertEquals(beforeNoteCount + 1, afterNotes.size)
        val newlyAddedNote = afterNotes.last()
        assertTrue("New note should contain tension resolution", newlyAddedNote.text.contains("اقدام رفع تعارض و بازبینی تنش"))
        assertTrue("New note should contain trigger topic", newlyAddedNote.text.contains(secondSeg.triggerTopic))
        assertFalse("New note should be pending / in progress", newlyAddedNote.isDone)

        // Clear sentiment analysis
        vm.clearSentimentAnalysis()
        assertNull(vm.audioSentimentAnalysis.value)
        assertNull(vm.activeSession.value.audioSentimentAnalysis)
    }

    @Test
    fun `test audio sentiment serialization and deserialization in meeting repository`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MeetingRepository(context)

        val sampleSentiment = AudioSentimentAnalysis(
            overallTone = "پرتنش با چالش‌های زنجیره سرد",
            harmonyScore = 65,
            tensionScore = 35,
            constructivePercentage = 60,
            neutralPercentage = 20,
            tensionPercentage = 20,
            dominantEmotion = "حساسیت بالا به زمان",
            summaryEvaluation = "جلسه با چالش‌های جدی بر سر دمای زنجیره سرد همراه بود.",
            flaggedSegments = listOf(
                HighTensionSegment(
                    timestampRange = "10:15 - 12:45",
                    speaker = "سخنران",
                    tensionLevel = "بحرانی",
                    triggerTopic = "افت دمای محموله واکسن",
                    quoteExcerpt = "«عدم رعایت پروتکل GDP غیرقابل قبول است.»",
                    reviewRecommendation = "جلسه فوری با تأمین‌کننده سنسورها",
                    isReviewed = true,
                    reviewNotes = "بررسی شد و صورتجلسه تنظیم گردید."
                )
            )
        )

        val sessionWithSentiment = MeetingSession(
            title = "جلسه پایش احساسات و تنش",
            audioSentimentAnalysis = sampleSentiment
        )

        repo.saveActiveSession(sessionWithSentiment)
        val loaded = repo.activeSession.value
        assertNotNull(loaded.audioSentimentAnalysis)
        assertEquals("پرتنش با چالش‌های زنجیره سرد", loaded.audioSentimentAnalysis?.overallTone)
        assertEquals(65, loaded.audioSentimentAnalysis?.harmonyScore)
        assertEquals(35, loaded.audioSentimentAnalysis?.tensionScore)
        assertEquals(1, loaded.audioSentimentAnalysis?.flaggedSegments?.size)

        val loadedSeg = loaded.audioSentimentAnalysis!!.flaggedSegments.first()
        assertEquals("سخنران", loadedSeg.speaker)
        assertEquals("بحرانی", loadedSeg.tensionLevel)
        assertEquals("افت دمای محموله واکسن", loadedSeg.triggerTopic)
        assertTrue(loadedSeg.isReviewed)
        assertEquals("بررسی شد و صورتجلسه تنظیم گردید.", loadedSeg.reviewNotes)

        // Test export & import JSON
        val exportedJson = repo.exportFullDatabaseJson()
        assertTrue(exportedJson.contains("پرتنش با چالش‌های زنجیره سرد"))
        assertTrue(exportedJson.contains("افت دمای محموله واکسن"))

        val importSuccess = repo.importFullDatabaseJson(exportedJson)
        assertTrue(importSuccess)
    }

    @Test
    fun `test pdf report generation with audio sentiment and high tension segments`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sampleSentiment = AudioSentimentAnalysis(
            overallTone = "هم‌افزا و سازنده",
            harmonyScore = 88,
            tensionScore = 12,
            flaggedSegments = listOf(
                HighTensionSegment(
                    timestampRange = "15:00 - 17:30",
                    speaker = "مدیر مالی",
                    tensionLevel = "متوسط",
                    triggerTopic = "بودجه پالت‌های ایزو",
                    quoteExcerpt = "«تخصیص بودجه نیازمند مجوز هیئت‌مدیره است.»",
                    reviewRecommendation = "ارسال متمم به مجمع",
                    isReviewed = false
                )
            )
        )
        val testSession = MeetingSession(
            title = "جلسه آزمایشی خروجی PDF با تحلیل احساسات",
            audioSentimentAnalysis = sampleSentiment
        )

        val pdfFile = PdfReportGenerator.generatePdf(context, testSession)
        assertNotNull(pdfFile)
        assertTrue("PDF file should exist", pdfFile.exists())
        assertTrue("PDF file should have non-zero size", pdfFile.length() > 0)
    }

    @Test
    fun `test Room ExecutiveSummaryEntity, ExecutiveSummaryDao, and ExecutiveSummaryRepository persistence`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.local.AtlasDatabase.createInMemory(context)
        db.executiveSummaryDao().clearAllSummaries()
        val execSummaryRepo = com.example.data.local.ExecutiveSummaryRepository(
            summaryDao = db.executiveSummaryDao(),
            minutesSearchRepository = com.example.data.local.MinutesSearchRepository(db.meetingMinutesDao())
        )

        val testSession = PredefinedScenarios.getScenario("board_cold_chain").copy(
            id = "SESSION-EXEC-ROOM-01",
            title = "جلسه هیئت‌مدیره زنجیره سرد و خلاصه اجرایی Room",
            audioFilePath = "/data/user/0/com.example/files/atlas_recordings/test_session.m4a",
            audioDurationSec = 185L
        )
        val geminiService = GeminiAiService()
        val analysisResult = geminiService.generatePostRecordingExecutiveSummary(
            audioFile = null,
            session = testSession,
            durationSec = 185L,
            customApiKey = ""
        )
        assertTrue(analysisResult.isSuccess)
        val analysis = analysisResult.getOrNull()!!
        assertTrue("Executive summary must not be blank", analysis.executiveSummary.isNotBlank())
        assertTrue("Key decisions must be extracted", analysis.keyDecisions.isNotEmpty())
        assertTrue("Action items must be extracted", analysis.actionItems.isNotEmpty())

        // Save to Room database
        val savedEntity = execSummaryRepo.saveExecutiveSummary(
            session = testSession,
            analysis = analysis,
            audioFilePath = "/data/user/0/com.example/files/atlas_recordings/test_session.m4a",
            isAutoGeneratedOnStop = true
        )

        assertTrue("Inserted Room row ID should be positive", savedEntity.id > 0)
        assertEquals("SESSION-EXEC-ROOM-01", savedEntity.sessionId)
        assertTrue(savedEntity.isAutoGeneratedOnStop)

        // Query back from Room database
        val loadedFromRoom = execSummaryRepo.getLatestSummaryForSessionOnce("SESSION-EXEC-ROOM-01")
        assertNotNull("ExecutiveSummaryEntity must be persisted in Room", loadedFromRoom)
        assertEquals(analysis.executiveSummary, loadedFromRoom!!.executiveSummary)
        assertEquals(analysis.keyDecisions.size, loadedFromRoom.getKeyDecisionsList().size)
        assertEquals(analysis.actionItems.size, loadedFromRoom.getActionItemsList().size)
        assertEquals(185L, loadedFromRoom.audioDurationSec)
    }

    @Test
    fun `test automatic Gemini executive summary generation and Room persistence immediately after stopping audio recording`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MeetingViewModel(app)
        vm.loadScenario("board_cold_chain")

        assertTrue("Auto-summarize on recording stop should be enabled by default", vm.autoSummarizeOnRecordingStop.value)
        assertFalse(vm.isAudioRecording.value)

        // 1. Start audio recording
        vm.toggleAudioRecording()
        assertTrue("Recording should now be active", vm.isAudioRecording.value)

        // 2. Stop audio recording -> must immediately trigger Gemini Executive Summary generation & Room persistence
        vm.toggleAudioRecording()
        assertFalse("Recording should now be stopped", vm.isAudioRecording.value)

        // Wait for async post-recording coroutine to complete
        for (i in 0 until 60) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (vm.lastPersistedRoomSummary.value != null && !vm.isAudioProcessingWithGemini.value) break
            kotlinx.coroutines.delay(25)
        }

        // Verify in-memory ViewModel state updated with Executive Summary & decisions
        val analysis = vm.audioMeetingAnalysis.value
        assertNotNull("AudioMeetingAnalysis must be automatically generated upon stopping recording", analysis)
        assertTrue("Generated executiveSummary must not be blank", analysis!!.executiveSummary.isNotBlank())
        assertTrue("Generated keyDecisions must not be empty", analysis.keyDecisions.isNotEmpty())
        assertTrue("Active session aiSummary must be updated with Executive Summary", vm.activeSession.value.aiSummary.contains("خلاصه اجرایی"))

        // Verify Room database persistence
        val persistedInRoom = vm.getLatestSavedExecutiveSummaryOnce()
        assertNotNull("ExecutiveSummaryEntity must be saved in Room database immediately after recording stops", persistedInRoom)
        assertEquals(vm.activeSession.value.id, persistedInRoom!!.sessionId)
        assertTrue("Entity must be flagged as auto-generated on stop", persistedInRoom.isAutoGeneratedOnStop)
        assertEquals(analysis.executiveSummary, persistedInRoom.executiveSummary)
        assertTrue("Room entity must contain key decisions", persistedInRoom.getKeyDecisionsList().isNotEmpty())
    }

    @Test
    fun `test Gemini extraction of responsible assignees and action items from meeting text and Room MemberTaskRepository persistence`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.local.AtlasDatabase.createInMemory(context)
        db.memberTaskDao().clearAllMemberTasks()
        val taskRepo = com.example.data.local.MemberTaskRepository(db.memberTaskDao())

        val testSession = PredefinedScenarios.getScenario("board_cold_chain").copy(
            id = "SESSION-MEMBER-TASKS-01",
            date = "1405/08/15"
        )
        val rawMeetingText = """
            مقرر گردید سخنران به صورت فوری کالیبراسیون سنسورهای دمای ناوگان یخچال‌دار را تا تاریخ 1405/08/18 تکمیل نماید.
            مدیر مالی موظف شد متمم بودجه پالت‌های استاندارد را تا فردا به هیئت‌مدیره ارسال کند.
        """.trimIndent()

        val geminiService = GeminiAiService()
        val extractionResult = geminiService.extractAssigneesAndActionItemsFromMeetingText(
            session = testSession,
            customMeetingText = rawMeetingText,
            customApiKey = ""
        )
        assertTrue(extractionResult.isSuccess)
        val extractedTasks = extractionResult.getOrNull()!!
        assertTrue("Must extract multiple tasks from meeting text and notes", extractedTasks.size >= 2)

        // Verify assignees and normalized calendar dates
        assertTrue("Atlas Team must be identified as a responsible assignee", extractedTasks.any { it.assigneeName.contains("سخنران") })
        assertTrue("All extracted tasks must have a valid YYYY/MM/DD calendarDate", extractedTasks.all { it.calendarDate.matches(Regex("\\d{4}/\\d{2}/\\d{2}")) })
        assertTrue("All extracted tasks must be flagged for calendar and member task list", extractedTasks.all { it.isAddedToCalendar && it.isAddedToMemberTaskList })

        // Save into Room database
        val savedTasks = taskRepo.saveExtractedMemberTasks("SESSION-MEMBER-TASKS-01", extractedTasks)
        assertEquals(extractedTasks.size, savedTasks.size)
        assertTrue("All saved tasks must have positive Room primary key IDs", savedTasks.all { it.id > 0 })

        // Query by Assignee in Room
        val rasoolTasks = taskRepo.getTasksForAssigneeOnce("سخنران")
        assertTrue("Room query by assignee must return Atlas Team's tasks", rasoolTasks.isNotEmpty())

        // Query by Calendar Date in Room
        val sampleDate = savedTasks.first().calendarDate
        val dateTasks = taskRepo.getTasksForCalendarDateOnce(sampleDate)
        assertTrue("Room query by calendarDate must return tasks scheduled on $sampleDate", dateTasks.isNotEmpty())

        // Toggle task completion in Room
        val toggled = taskRepo.toggleTaskDone(savedTasks.first())
        assertTrue(toggled.isDone)
        assertEquals("COMPLETED", toggled.status)
    }

    @Test
    fun `test ViewModel extractAndAutoAssignMemberTasksWithGemini automatically adds tasks to Room database and calendar`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MeetingViewModel(app)
        vm.loadScenario("board_cold_chain")

        val customText = "مقرر شد سخنران تا 1405/08/20 مستندات ممیزی زنجیره سرد را نهایی کند."
        vm.extractAndAutoAssignMemberTasksWithGemini(customText)

        for (i in 0 until 60) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (vm.lastExtractedMemberTasks.value.isNotEmpty() && !vm.isExtractingMemberTasks.value) break
            kotlinx.coroutines.delay(25)
        }

        val extracted = vm.lastExtractedMemberTasks.value
        assertTrue("Extracted member tasks must not be empty", extracted.isNotEmpty())
        assertTrue("Must contain task assigned to Atlas Team", extracted.any { it.assigneeName.contains("سخنران") })

        val persistedInRoom = vm.getMemberTasksForSessionFromRoomOnce()
        assertTrue("Extracted tasks must be automatically persisted in Room member_tasks table", persistedInRoom.isNotEmpty())
        assertTrue("Tasks in Room must have calendarDate populated for Calendar view", persistedInRoom.all { it.calendarDate.isNotBlank() })
    }

    @Test
    fun `test Gemini official meeting minutes draft PDF generation and sharing intent`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MeetingViewModel(app)
        vm.loadScenario("board_cold_chain")

        val session = vm.activeSession.value
        val geminiService = GeminiAiService()

        // 1. Test Gemini AI template synthesis of official meeting minutes draft
        val draftResult = geminiService.generateOfficialMinutesDraftWithGemini(
            session = session,
            templateStyle = "قالب رسمی هیئت‌مدیره (ایزو ۹۰۰۱)",
            customApiKey = ""
        )
        assertTrue("Gemini official minutes draft generation must succeed", draftResult.isSuccess)
        val draft = draftResult.getOrNull()!!
        assertTrue("Document number must not be blank", draft.documentNumber.isNotBlank())
        assertTrue("Draft title must not be blank", draft.draftTitle.isNotBlank())
        assertTrue("Key points summary must be extracted", draft.keyPointsSummary.isNotEmpty())
        assertTrue("Formal resolutions must be synthesized", draft.formalResolutions.isNotEmpty())
        assertTrue("Assigned commitments must be synthesized", draft.assignedCommitments.isNotEmpty())
        assertTrue("Formal preamble and closing clause must not be blank", draft.formalPreamble.isNotBlank() && draft.closingClause.isNotBlank())

        // 2. Test PDF generation for the official minutes draft
        val pdfFile = PdfReportGenerator.generateOfficialMinutesDraftPdf(app, session, draft)
        assertNotNull("Generated PDF file must not be null", pdfFile)
        assertTrue("Generated PDF file must exist on disk", pdfFile.exists())
        assertTrue("Generated PDF file must have non-zero bytes", pdfFile.length() > 0)

        // 3. Test Share Intent creation for the official minutes draft PDF
        val shareIntent = PdfReportGenerator.createShareDraftPdfIntent(app, pdfFile, draft)
        assertEquals(android.content.Intent.ACTION_SEND, shareIntent.action)
        assertEquals("application/pdf", shareIntent.type)
        assertNotNull("Share intent must contain EXTRA_STREAM URI", shareIntent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM))
        val shareText = shareIntent.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""
        assertTrue("Share text must include document number and title", shareText.contains(draft.documentNumber))

        // 4. Test ViewModel end-to-end generateAndCreateOfficialDraftPdf flow
        vm.setSelectedDraftTemplate("قالب حقوقی و قراردادی")
        vm.generateAndCreateOfficialDraftPdf(app, autoShare = false)

        for (i in 0 until 60) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (vm.officialMinutesDraft.value != null && !vm.isGeneratingMinutesDraft.value) break
            kotlinx.coroutines.delay(25)
        }

        val vmDraft = vm.officialMinutesDraft.value
        assertNotNull("ViewModel must populate officialMinutesDraft state", vmDraft)
        assertEquals("قالب حقوقی و قراردادی", vmDraft!!.templateStyle)
        assertTrue("Generated PDF path must be recorded in OfficialMinutesDraft", !vmDraft.generatedPdfPath.isNullOrBlank())
        assertNotNull("ViewModel must hold generatedDraftPdfFile reference", vm.generatedDraftPdfFile.value)
        assertTrue("ViewModel generatedDraftPdfFile must exist", vm.generatedDraftPdfFile.value!!.exists())
    }

    @Test
    fun `test real-time voice-to-text recording immediately summarizes transcript into key decisions and action items on stop`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MeetingViewModel(app)
        vm.loadScenario("board_cold_chain")

        // 1. Start real-time voice-to-text recording
        vm.startContinuousTranscription()
        assertTrue("Real-time voice-to-text should be active", vm.isContinuousTranscribing.value)

        // 2. Capture live voice-to-text segments during the meeting
        vm.addTranscriptionSegmentManually(
            text = "مصوب شد سامانه پایش هوشمند دمای سردخانه مرکزی با نظارت سخنران تا پایان هفته راه‌اندازی شود.",
            speaker = "سخنران"
        )
        vm.addTranscriptionSegmentManually(
            text = "مدیر مالی موظف گردید اعتبار خرید سنسورهای کالیبره را تا فردا تخصیص دهد.",
            speaker = "مدیر مالی"
        )

        // 3. Stop real-time voice-to-text recording -> must immediately trigger Gemini transcript summarization
        vm.stopContinuousTranscription(triggerAutoSummary = true)
        assertFalse("Real-time voice-to-text should now be stopped", vm.isContinuousTranscribing.value)

        for (i in 0 until 60) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (vm.transcriptionAiAnalysis.value != null && !vm.isTranscriptionAiLoading.value) break
            kotlinx.coroutines.delay(25)
        }

        // 4. Verify transcript is summarized into key decisions and action items immediately after stop
        val analysis = vm.transcriptionAiAnalysis.value
        assertNotNull("TranscriptionAiAnalysis must be populated immediately after voice-to-text stops", analysis)
        assertTrue("Executive summary must not be blank", analysis!!.executiveSummary.isNotBlank())
        assertTrue("Key decisions must be extracted from transcript", analysis.keyDecisions.isNotEmpty())
        assertTrue("Action items must be extracted from transcript", analysis.actionItems.isNotEmpty())
        assertTrue(
            "Atlas Team must be assigned in extracted action items",
            analysis.actionItems.any { it.assignee.contains("سخنران") || it.title.contains("سخنران") }
        )
    }

    @Test
    fun `test quarterly board analytics with Room action item volume and Room deadline notifications`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val vm = MeetingViewModel(app)
        vm.loadScenario("board_cold_chain")

        // 1. Extract and persist board member action items into Room
        vm.extractAndAutoAssignMemberTasksWithGemini(
            customMeetingText = "مصوب شد سخنران تا پایان هفته سامانه پایش زنجیره سرد را راه‌اندازی نماید. همچنین سارا احمدی موظف شد گزارش حسابرسی انبار را تا فردا تحویل دهد."
        )
        for (i in 0 until 60) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (!vm.isExtractingMemberTasks.value && vm.lastExtractedMemberTasks.value.isNotEmpty()) break
            kotlinx.coroutines.delay(25)
        }

        val roomTasks = vm.getMemberTasksForSessionFromRoomOnce()
        assertTrue("Room member_tasks table must contain extracted board member tasks", roomTasks.isNotEmpty())

        // 2. Verify Quarterly BoardAnalyticsEngine incorporates Room MemberTaskEntity volume and attendance frequency
        val analytics = com.example.ui.components.BoardAnalyticsEngine.computeAnalytics(
            activeSession = vm.activeSession.value,
            roomAttendees = vm.roomAttendees.value,
            archiveList = vm.archiveList.value,
            selectedBoardMemberName = null,
            roomMemberTasks = roomTasks
        )
        assertTrue("Board members analytics must not be empty", analytics.boardMembers.isNotEmpty())
        val rasoolStats = analytics.boardMembers.find { it.name.contains("سخنران") }
        assertNotNull("Atlas Team must be present in quarterly board member analytics", rasoolStats)
        assertTrue("Attendance frequency over last quarter must be > 0", rasoolStats!!.sessionsPresent > 0)
        assertTrue("Volume of assigned action items must include Room tasks", rasoolStats.totalActions >= 2)

        // 3. Verify Smart Meeting Minutes Filter state updates (محدوده جلسه, وضعیت مصوبه, عضو مسئول)
        vm.updateListFilterScope("internal")
        vm.updateListFilterStatus("IN_PROGRESS")
        vm.updateListFilterMember("سخنران")
        assertEquals("internal", vm.listFilterScope.value)
        assertEquals("IN_PROGRESS", vm.listFilterStatus.value)
        assertEquals("سخنران", vm.listFilterMember.value)
        vm.resetListFilters()
        assertEquals("ALL", vm.listFilterScope.value)
        assertEquals("ALL", vm.listFilterStatus.value)
        assertEquals("ALL", vm.listFilterMember.value)

        // 4. Verify Room-backed board member deadline notification dispatch
        val pendingTask = roomTasks.first { !it.isDone }
        val singleSent = com.example.util.MeetingNotificationHelper.sendRoomMemberTaskDeadlineReminder(app, pendingTask)
        assertTrue("Single Room member task deadline notification should be sent", singleSent)

        val bulkSentCount = com.example.util.MeetingNotificationHelper.sendAllUpcomingRoomTaskDeadlineReminders(
            context = app,
            tasks = roomTasks,
            assigneeFilter = "ALL"
        )
        assertTrue("Bulk Room upcoming deadline notifications should be sent for pending tasks", bulkSentCount > 0)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }

    @Test
    fun `test finalized meeting minutes conversion to standard PDF with corporate letterhead and digital signature placeholders`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MeetingViewModel(app)
        vm.loadScenario("board_cold_chain")

        // 1. Verify default corporate letterhead & digital signature placeholder configuration
        val initialSession = vm.activeSession.value
        assertTrue("Session should be finalized by default", initialSession.isFinalized)
        assertEquals(3, initialSession.corporatePdfConfig.signatories.size)
        assertTrue(
            "Atlas Team must be included in digital signature placeholders",
            initialSession.corporatePdfConfig.signatories.any { it.signerName.contains("سخنران") }
        )

        // 2. Customize corporate letterhead config & toggle finalization state
        val customConfig = initialSession.corporatePdfConfig.copy(
            companyNameFa = "هلدینگ دارویی و زنجیره سرد اطلس فارما (سهامی خاص)",
            documentNumber = "ATS-CORP-1405-990",
            classificationLevel = "محرمانه مدیریتی - ویژه هیئت‌مدیره",
            includeSecurityWatermark = true
        )
        vm.updateCorporatePdfConfig(customConfig)
        vm.toggleSessionFinalized(true)

        assertEquals("ATS-CORP-1405-990", vm.activeSession.value.corporatePdfConfig.documentNumber)
        assertTrue(vm.activeSession.value.isFinalized)

        // 3. Convert finalized meeting minutes to standard Corporate PDF via ViewModel
        val generatedFile = vm.convertFinalizedSessionToCorporatePdf(app, autoShare = false)
        assertNotNull("Generated finalized corporate PDF file must not be null", generatedFile)
        assertTrue("Generated corporate PDF file must exist on disk", generatedFile.exists())
        assertTrue("Generated corporate PDF file must have non-zero size", generatedFile.length() > 0)
        assertTrue("Generated file name must start with Atlas_Corporate_Minutes_", generatedFile.name.startsWith("Atlas_Corporate_Minutes_"))

        // 4. Verify activeSession records the generated PDF path and lastGeneratedCorporatePdfFile state
        assertEquals(generatedFile.absolutePath, vm.activeSession.value.corporatePdfConfig.lastGeneratedPdfPath)
        assertEquals(generatedFile.absolutePath, vm.lastGeneratedCorporatePdfFile.value?.absolutePath)

        // 5. Verify PDF content stream contains valid PDF header, corporate letterhead, and digital signature placeholders
        val pdfText = generatedFile.readText(Charsets.UTF_8)
        assertTrue("File must begin with standard %PDF-1.4 header", pdfText.startsWith("%PDF-1.4"))
        assertTrue("PDF must embed CORPORATE_LETTERHEAD metadata", pdfText.contains("CORPORATE_LETTERHEAD"))
        assertTrue("PDF must embed custom document number ATS-CORP-1405-990", pdfText.contains("ATS-CORP-1405-990"))
        assertTrue("PDF must embed FINALIZED_OFFICIAL status", pdfText.contains("FINALIZED_OFFICIAL"))
        assertTrue("PDF must embed DIGITAL_SIGNATURE_PLACEHOLDER blocks", pdfText.contains("DIGITAL_SIGNATURE_PLACEHOLDER"))
        assertTrue("PDF must embed Atlas Team's PKI certificate serial placeholder", pdfText.contains("PKI-ATS-2026-RN01"))
    }

    @Test
    fun `test Room BoardScheduleRepository upcoming board meetings and historical records calendar integration`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.local.AtlasDatabase.createInMemory(context)
        db.boardScheduleDao().clearAllScheduleItems()

        val minutesRepo = com.example.data.local.MinutesSearchRepository(db.meetingMinutesDao())
        val scheduleRepo = com.example.data.local.BoardScheduleRepository(
            scheduleDao = db.boardScheduleDao(),
            minutesSearchRepository = minutesRepo
        )

        val activeSession = PredefinedScenarios.getScenario("board_cold_chain")
        scheduleRepo.syncAndSeedBoardSchedule(
            activeSession = activeSession,
            archiveList = emptyList(),
            referenceDate = "1405/08/15"
        )

        // 1. Verify both upcoming meetings and historical records are stored in Room
        val allItems = scheduleRepo.getAllScheduleItemsOnce()
        assertTrue("Board schedule table must contain seeded and synced meetings", allItems.size >= 6)

        val upcoming = scheduleRepo.getUpcomingMeetingsOnce("1405/08/15")
        val historical = scheduleRepo.getHistoricalMeetingsOnce("1405/08/15")
        assertTrue("Upcoming board meetings must not be empty", upcoming.isNotEmpty())
        assertTrue("Historical board meeting records must not be empty", historical.isNotEmpty())
        assertTrue("Historical records must include 1405/08/15 cold-chain board session", historical.any { it.sessionId == "SES-BOARD-COLD-CHAIN" })

        // 2. Schedule a new upcoming board meeting into Room
        val newlyScheduled = scheduleRepo.scheduleUpcomingMeeting(
            title = "جلسه فوق‌العاده هیئت‌مدیره: تصویب بودجه سردخانه هوشمند جنوب",
            date = "۱۴۰۵/۰۸/۲۸",
            startTime = "11:00",
            durationMins = 120,
            location = "سالن کنفرانس مرکزی",
            chairpersonName = "سخنران",
            agendaSummary = "بررسی طرح توسعه زنجیره سرد جنوب کشور و تخصیص اعتبار ارزی"
        )
        assertEquals("1405/08/28", newlyScheduled.date)
        assertTrue(newlyScheduled.isUpcoming)

        val dateQueryResult = scheduleRepo.getMeetingsForDateOnce("1405/08/28")
        assertEquals(1, dateQueryResult.size)
        assertEquals("جلسه فوق‌العاده هیئت‌مدیره: تصویب بودجه سردخانه هوشمند جنوب", dateQueryResult.first().title)

        // 3. Toggle scheduled meeting status from UPCOMING to COMPLETED (historical record)
        val completedRecord = scheduleRepo.updateMeetingScheduleStatus(
            sessionId = newlyScheduled.sessionId,
            newStatus = com.example.data.local.BoardScheduleEntity.STATUS_COMPLETED
        )
        assertNotNull(completedRecord)
        assertTrue(completedRecord!!.isHistorical)
        assertTrue(completedRecord.isFinalized)
    }

    @Test
    fun `test Android audio recording and instant Speech-to-Text auto-typing into meeting minutes`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MeetingViewModel(app)
        vm.loadScenario("board_cold_chain")
        vm.clearAllTranscriptions()

        val initialNoteCount = vm.activeSession.value.notes.size
        assertTrue("Auto-commit speech to notes should be enabled by default", vm.autoCommitSpeechToNotes.value)

        // 1. Start combined Android audio recording + continuous Speech-to-Text
        vm.toggleCombinedAudioAndSpeechToText()
        assertTrue("Audio recording must be active", vm.isAudioRecording.value)
        assertTrue("Continuous Speech-to-Text must be active", vm.isContinuousTranscribing.value)

        // 2. Simulate instantaneous Speech-to-Text recognition for Atlas Team
        vm.setTranscribingSpeaker("سخنران")
        val spokenResolution = "مصوب شد استقرار کامل سنسورهای تله‌متری سردخانه مرکزی تا پایان هفته انجام شود."
        vm.simulateInstantSpeechToMinutesDictation(spokenResolution)

        // 3. Verify liveTypedMinutesBuffer is instantaneously populated with speaker & text
        val liveMinutesText = vm.liveTypedMinutesBuffer.value
        assertTrue("Live typed minutes buffer must include speaker name", liveMinutesText.contains("سخنران"))
        assertTrue("Live typed minutes buffer must include spoken resolution text", liveMinutesText.contains(spokenResolution))

        // 4. Verify spoken text is added to session.transcriptions and auto-committed to session.notes
        val transcriptions = vm.activeSession.value.transcriptions
        assertEquals(1, transcriptions.size)
        assertEquals(spokenResolution, transcriptions.first().text)
        assertTrue(transcriptions.first().isCommittedToNotes)

        val updatedNotes = vm.activeSession.value.notes
        assertEquals(initialNoteCount + 1, updatedNotes.size)
        assertEquals(spokenResolution, updatedNotes.first().text)
        assertEquals("سخنران", updatedNotes.first().speaker)
        assertEquals("🚨 مصوبه رسمی", updatedNotes.first().type)

        // 5. Stop combined recording + Speech-to-Text
        vm.toggleCombinedAudioAndSpeechToText()
        assertFalse("Audio recording should now be stopped", vm.isAudioRecording.value)
    }

    @Test
    fun `test Gemini API automatic analysis of typed meeting text extracts formal minutes decisions and per-person assigned tasks`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MeetingViewModel(app)
        vm.loadScenario("board_cold_chain")

        val sampleTypedMeetingText = """
            [00:02:15] سخنران: مصوب شد سامانه پایش برخط دمای ناوگان زنجیره سرد با مسئولیت سخنران تا پایان هفته عملیاتی گردد.
            [00:05:40] سخنران: مقرر گردید سخنران گزارش ممیزی استانداردهای انبارش دارویی را تا ۲۰ آبان به هیئت‌مدیره ارائه نماید.
            [00:08:10] مدیر مالی: تصمیم گرفته شد مدیر مالی متمم بودجه خرید سنسورهای تله‌متری را تا فردا نهایی و ابلاغ کند.
        """.trimIndent()

        vm.updateLiveTypedMinutesBuffer(sampleTypedMeetingText)
        vm.analyzeTypedMeetingTextWithGemini(sampleTypedMeetingText, isAutoTriggered = false)

        // Wait for coroutine completion
        var attempts = 0
        while ((vm.isAnalyzingTypedMinutes.value || vm.typedMinutesAnalysis.value == null) && attempts < 80) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(25)
            attempts++
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val analysis = vm.typedMinutesAnalysis.value
        assertNotNull("Gemini typed meeting minutes analysis must not be null", analysis)

        // 1. Verify extracted formal meeting minutes (صورت‌جلسه)
        assertTrue(
            "Extracted formal minutes text must not be blank",
            analysis!!.formalMinutesText.isNotBlank()
        )
        assertTrue(
            "Formal minutes must reference the meeting context",
            analysis.formalMinutesText.contains("صورت‌جلسه")
        )

        // 2. Verify extracted decisions made (تصمیمات اتخاذ شده)
        assertTrue(
            "Extracted decisions list must contain the decisions from typed text",
            analysis.decisionsMade.size >= 2
        )
        assertTrue(
            "Decisions must include cold-chain telemetry decision",
            analysis.decisionsMade.any { it.contains("زنجیره سرد") || it.contains("سنسور") }
        )

        // 3. Verify extracted tasks assigned to each person (وظایف محول شده به هر شخص)
        assertTrue(
            "Assigned tasks by person must contain tasks for multiple individuals",
            analysis.assignedTasksByPerson.size >= 3
        )
        assertTrue(
            "Atlas Team must have an assigned task extracted from typed text",
            analysis.assignedTasksByPerson.any { it.personName.contains("سخنران") && it.taskTitle.contains("زنجیره سرد") }
        )
        assertTrue(
            "Mana Kazerani must have an assigned task extracted from typed text",
            analysis.assignedTasksByPerson.any { it.personName.contains("سخنران") && it.taskTitle.contains("ممیزی") }
        )

        // 4. Verify per-person tasks are persisted in Room member_tasks table
        val roomTasks = vm.getMemberTasksForSessionFromRoomOnce()
        assertTrue(
            "Extracted per-person tasks must be persisted in Room member_tasks table",
            roomTasks.any { it.assigneeName.contains("سخنران") }
        )
    }

    @Test
    fun `test main page interactive monthly calendar date filter and top search Date Range Picker`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MeetingViewModel(app)

        val allBoardMeetings = com.example.data.local.BoardScheduleRepository.buildDefaultBoardScheduleSeed()
        assertTrue(
            "Board schedule seed should contain holding and board meetings",
            allBoardMeetings.size >= 7
        )

        // 1. Verify interactive monthly calendar single-date filter (17 Aban 1405 -> Multi-Tenant & 5PL)
        vm.selectCalendarDateFilter("1405/08/17")
        assertEquals("1405/08/17", vm.selectedCalendarDateFilter.value)
        val filteredBySingleDate = vm.getCurrentFilteredHomeMeetings(allBoardMeetings)
        assertEquals(1, filteredBySingleDate.size)
        assertTrue(
            "Single-date filter on 1405/08/17 must return Multi-Tenant 5PL meeting",
            filteredBySingleDate.first().title.contains("Multi-Tenant")
        )

        // Toggling the same date clears the single-date filter
        vm.selectCalendarDateFilter("1405/08/17")
        assertEquals("", vm.selectedCalendarDateFilter.value)
        assertEquals(allBoardMeetings.size, vm.getCurrentFilteredHomeMeetings(allBoardMeetings).size)

        // 2. Verify Top Search Date Range Picker (انتخابگر محدوده زمانی: 1405/08/16 تا 1405/08/17)
        vm.updateDateRangeFilter("۱۴۰۵/۰۸/۱۶", "۱۴۰۵/۰۸/۱۷")
        assertEquals("1405/08/16", vm.dateRangeStartFilter.value)
        assertEquals("1405/08/17", vm.dateRangeEndFilter.value)

        val filteredByDateRange = vm.getCurrentFilteredHomeMeetings(allBoardMeetings)
        assertEquals(
            "Date range 1405/08/16..1405/08/17 must return the 2 holding sessions on 16 and 17 Aban",
            2,
            filteredByDateRange.size
        )
        assertTrue(filteredByDateRange.any { it.title.contains("TTAC") })
        assertTrue(filteredByDateRange.any { it.title.contains("Multi-Tenant") })

        // 3. Verify combined Top Search query + Date Range Picker
        vm.updateHomeSearchQuery("TTAC")
        val combinedFiltered = vm.getCurrentFilteredHomeMeetings(allBoardMeetings)
        assertEquals(1, combinedFiltered.size)
        assertEquals("1405/08/16", combinedFiltered.first().date)

        // 4. Verify clearing all filters restores full list
        vm.clearAllHomeDateAndSearchFilters()
        assertEquals("", vm.homeSearchQuery.value)
        assertEquals("", vm.dateRangeStartFilter.value)
        assertEquals("", vm.dateRangeEndFilter.value)
        assertEquals(allBoardMeetings.size, vm.getCurrentFilteredHomeMeetings(allBoardMeetings).size)
    }
}






