package net.hrapp.hr.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

object OemCompatibilityHelper {

    data class OemInstruction(
        val title: String,
        val steps: List<String>,
        val settingsIntent: Intent? = null
    )

    fun getManufacturer(): String = Build.MANUFACTURER.lowercase()

    fun getDeviceModel(): String = Build.MODEL

    fun needsSpecialSetup(): Boolean {
        val manufacturer = getManufacturer()
        return manufacturer.contains("xiaomi") ||
                manufacturer.contains("redmi") ||
                manufacturer.contains("poco") ||
                manufacturer.contains("samsung") ||
                manufacturer.contains("huawei") ||
                manufacturer.contains("honor") ||
                manufacturer.contains("oneplus") ||
                manufacturer.contains("oppo") ||
                manufacturer.contains("vivo") ||
                manufacturer.contains("realme")
    }

    fun getInstructions(): OemInstruction {
        val manufacturer = getManufacturer()

        return when {
            manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("poco") -> OemInstruction(
                title = "Xiaomi / Redmi / POCO Ayarları",
                steps = listOf(
                    "1. Ayarlar → Uygulamalar → Uygulamaları yönet → Heart Monitor",
                    "2. 'Otomatik başlatma' seçeneğini AÇ",
                    "3. 'Pil tasarrufu' → 'Kısıtlama yok' seçin",
                    "4. Son uygulamalar ekranında uygulamayı aşağı kaydırarak KİLİTLEYİN",
                    "5. Güvenlik uygulaması → Pil → Arka plan ayarları → Heart Monitor → Arka plan etkinliğine izin ver"
                ),
                settingsIntent = createXiaomiAutoStartIntent()
            )

            manufacturer.contains("samsung") -> OemInstruction(
                title = "Samsung Ayarları",
                steps = listOf(
                    "1. Ayarlar → Pil ve cihaz bakımı → Pil",
                    "2. 'Arka plan kullanım sınırları' → 'Hiçbir zaman uyumayan uygulamalar'a Heart Monitor'ü ekleyin",
                    "3. 'Uyarlanabilir pil' seçeneğini KAPATIN",
                    "4. Ayarlar → Uygulamalar → Heart Monitor → Pil → 'Sınırsız' seçin"
                ),
                settingsIntent = createSamsungBatteryIntent()
            )

            manufacturer.contains("huawei") ||
            manufacturer.contains("honor") -> OemInstruction(
                title = "Huawei / Honor Ayarları",
                steps = listOf(
                    "1. Ayarlar → Pil → Uygulama başlatma → Heart Monitor",
                    "2. 'Manuel olarak yönet' seçeneğini AÇ",
                    "3. Üç seçeneği de açın: Otomatik başlatma, İkincil başlatma, Arka planda çalış",
                    "4. Telefon Yöneticisi → Pil → Ayarlar → Korunan uygulamalar → Heart Monitor'ü ekleyin"
                ),
                settingsIntent = createHuaweiAutoStartIntent()
            )

            manufacturer.contains("oneplus") -> OemInstruction(
                title = "OnePlus Ayarları",
                steps = listOf(
                    "1. Ayarlar → Pil → Pil optimizasyonu → Heart Monitor → 'Optimize etme' seçin",
                    "2. Ayarlar → Pil → Gelişmiş optimizasyon → KAPATIN",
                    "3. Son uygulamalar → Heart Monitor'ü KİLİTLEYİN (kilit ikonu)"
                ),
                settingsIntent = createOnePlusBatteryIntent()
            )

            manufacturer.contains("oppo") ||
            manufacturer.contains("realme") -> OemInstruction(
                title = "OPPO / Realme Ayarları",
                steps = listOf(
                    "1. Ayarlar → Pil → Güç tasarrufu → Heart Monitor → 'Arka planda çalışmaya izin ver'",
                    "2. Ayarlar → Uygulama yönetimi → Heart Monitor → 'Otomatik başlatma' → AÇ",
                    "3. Güvenlik merkezi → Gizlilik izinleri → Başlangıç yöneticisi → Heart Monitor → AÇ"
                ),
                settingsIntent = createOppoAutoStartIntent()
            )

            manufacturer.contains("vivo") -> OemInstruction(
                title = "Vivo Ayarları",
                steps = listOf(
                    "1. Ayarlar → Pil → Yüksek arka plan güç tüketimi → Heart Monitor → AÇ",
                    "2. Ayarlar → Daha fazla ayar → Uygulamalar → Otomatik başlatma → Heart Monitor → AÇ",
                    "3. i Manager → Uygulama yöneticisi → Autostart yöneticisi → Heart Monitor → AÇ"
                ),
                settingsIntent = createVivoAutoStartIntent()
            )

            else -> OemInstruction(
                title = "Pil Ayarları",
                steps = listOf(
                    "1. Ayarlar → Pil → Heart Monitor → 'Arka plan kısıtlaması yok' seçin",
                    "2. Ayarlar → Uygulamalar → Heart Monitor → Pil → 'Sınırsız' seçin"
                )
            )
        }
    }

    fun tryOpenAutoStartSettings(context: Context): Boolean {
        val instructions = getInstructions()
        val intent = instructions.settingsIntent ?: return false

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun createXiaomiAutoStartIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun createSamsungBatteryIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun createHuaweiAutoStartIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun createOnePlusBatteryIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun createOppoAutoStartIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun createVivoAutoStartIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
