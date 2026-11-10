package com.example.data

object PredefinedScenarios {

    fun getScenario(key: String): MeetingSession {
        return when (key) {
            "board_cold_chain" -> MeetingSession(
                id = "SES-BOARD-COLD-CHAIN",
                title = "جلسه راهبردی هیئت‌مدیره در خصوص توسعه زنجیره سرد و ناوگان 5PL",
                scope = "external_supplier",
                durationMins = 120,
                extCompany = "شرکت چاپار چابک دارو رسان",
                extRef = "RN-OPS-PLB-2026",
                location = "سالن کنفرانس مرکزی",
                calendarType = "jalali",
                date = "1405/08/15",
                startTime = "10:00",
                members = listOf(
                    Member(name = "سخنران", title = "مدیر عملیات", unit = "لجستیک و زنجیره تأمین", isPresent = true),
                    Member(name = "سخنران", title = "مدیرعامل", unit = "مدیریت ارشد", isPresent = true),
                    Member(name = "سخنران", title = "رئیس هیئت‌مدیره", unit = "هیئت‌مدیره", isPresent = true),
                    Member(name = "سخنران", title = "مدیر مالی", unit = "مالی و حسابداری", isPresent = true)
                ),
                attachments = listOf(
                    Attachment(name = "پلی‌بوک جامع عملیات لجستیک.pdf", refCode = "RN-OPS-PLB-2026-003")
                ),
                notes = listOf(
                    MeetingNote(
                        timeFormatted = "00:15:20",
                        speaker = "سخنران",
                        text = "مصوب شد استقرار کامل برج کنترل هوشمند زنجیره سرد با پایش برخط دمای ۲ تا ۸ درجه انجام شود.",
                        type = "🚨 مصوبه رسمی",
                        due = "۱۴۰۵/۰۸/۳۰",
                        isDone = true
                    ),
                    MeetingNote(
                        timeFormatted = "00:34:10",
                        speaker = "سخنران",
                        text = "تخصیص سقف بودجه ۵,۵۰۰,۰۰۰,۰۰۰ ریال جهت نوسازی کانتینرهای یخچالی ناوگان 5PL.",
                        type = "💰 مصوبه مالی",
                        due = "۱۴۰۵/۰۹/۱۰",
                        isDone = false
                    )
                ),
                aiSummary = "خلاصه اجرایی جلسه هیئت‌مدیره: تصویب استقرار برج کنترل زنجیره سرد دارویی، اتصال به سامانه TTAC و تخصیص بودجه نوسازی ناوگان 5PL.",
                opsData = OpsData(
                    enabled = true,
                    fleetEff = "96.4% تحویل موفق",
                    coldChain = "پایدار و منطبق با استاندارد (2°C تا 8°C)",
                    palletModel = "تثبیت تعرفه پلکانی انبارش دارویی",
                    budget = "۵,۵۰۰,۰۰۰,۰۰۰ ریال",
                    summary = "تایید نهایی استقرار برج کنترل هوشمند و اتصال به پروتکل‌های TTAC"
                ),
                hrData = HrData(enabled = false)
            )

            "hr_interview" -> MeetingSession(
                id = "SES-HR-INTERVIEW",
                title = "کمیته تخصصی جذب و ارزیابی سرپرست عملیات انبار",
                scope = "interview_hr",
                durationMins = 60,
                extCompany = "کاندیدای مدیریت انبار",
                extRef = "HR-1405-12",
                location = "اتاق مصاحبه تخصصی",
                calendarType = "jalali",
                date = "1405/08/16",
                startTime = "14:00",
                members = listOf(
                    Member(name = "سخنران", title = "مدیر عملیات", unit = "زنجیره تأمین", isPresent = true),
                    Member(name = "مدیر منابع انسانی", title = "HR Manager", unit = "سرمایه انسانی", isPresent = true)
                ),
                attachments = listOf(
                    Attachment(name = "رزومه تخصصی و سوابق اجرایی.pdf", refCode = "CV-2026")
                ),
                notes = listOf(
                    MeetingNote(
                        timeFormatted = "00:25:00",
                        speaker = "سخنران",
                        text = "تایید صلاحیت فنی مهندس آرش کاظمی در حوزه مدیریت انبار سرد WMS و استانداردهای GDP.",
                        type = "🚨 مصوبه رسمی",
                        due = "فوری",
                        isDone = true
                    )
                ),
                aiSummary = "خلاصه اجرایی کمیته جذب: تایید استخدام سرپرست عملیات انبار سرد با امتیاز ۹۱ از ۱۰۰ پس از ارزیابی تخصصی فرآیندهای انبارداری و زنجیره تامین.",
                hrData = HrData(
                    enabled = true,
                    candidate = "مهندس آرش کاظمی",
                    position = "سرپرست عملیات انبار سرد و WMS",
                    score = "91 از 100",
                    decision = "تایید قطعی جهت استخدام (Strong Hire)",
                    notes = "تسلط کامل بر فرآیندهای RFID، چیدمان پالت و استانداردهای زنجیره سرد دارویی."
                ),
                opsData = OpsData(enabled = false)
            )

            "supplier_contract" -> MeetingSession(
                id = "SES-SUPPLIER-CONTRACT",
                title = "جلسه مذاکره و عقد قرارداد تامین تجهیزات رهگیری ناوگان و پالت",
                scope = "external_contract",
                durationMins = 90,
                extCompany = "شرکت تامین تجهیزات هوشمند",
                extRef = "CONT-RFID-1405",
                location = "سالن مذاکرات تجاری",
                calendarType = "jalali",
                date = "1405/08/17",
                startTime = "11:30",
                members = listOf(
                    Member(name = "سخنران", title = "مدیر عملیات", unit = "لجستیک", isPresent = true),
                    Member(name = "سخنران", title = "مدیر مالی", unit = "مالی", isPresent = true)
                ),
                attachments = listOf(
                    Attachment(name = "پیش‌نویس قرارداد خرید سنسورهای دما.docx", refCode = "DRAFT-09")
                ),
                notes = listOf(
                    MeetingNote(
                        timeFormatted = "00:40:00",
                        speaker = "سخنران",
                        text = "عقد قرارداد خرید ۵۰۰ عدد سنسور هوشمند دما و پالت استاندارد دارویی با گارانتی ۲۴ ماهه.",
                        type = "📌 تعهد اجرایی",
                        due = "۱۴۰۵/۰۸/۲۵",
                        isDone = false
                    )
                ),
                aiSummary = "خلاصه اجرایی جلسه قرارداد: توافق نهایی بر سر تامین پالت دارویی و سنسورهای پایش برخط دما و رطوبت ناوگان با تضمین SLA.",
                opsData = OpsData(enabled = false),
                hrData = HrData(enabled = false)
            )

            else -> MeetingSession(id = "SES-" + System.currentTimeMillis())
        }
    }
}
