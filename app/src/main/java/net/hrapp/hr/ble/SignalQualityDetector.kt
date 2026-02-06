package net.hrapp.hr.ble

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Sinyal Kalitesi Dedektörü
 *
 * AFib, bradikardi ve taşikardi hastalarını destekleyen,
 * gürültüyü gerçek kardiyak veriden ayıran algoritma.
 *
 * Temel özellikler:
 * - 140-170 BPM gürültü bandı yoğunluğu tespiti
 * - Sample Entropy (kaotik vs düzenli sinyal ayrımı)
 * - Kademeli window sistemi (5s hızlı → 15s güvenilir → 30s kesin)
 * - AFib koruma: Yüksek entropy = gerçek düzensizlik, reddetme
 */
class SignalQualityDetector {

    companion object {
        private const val TAG = "SignalQualityDetector"

        // Window boyutları (saniye cinsinden, 1 Hz varsayımı)
        private const val QUICK_WINDOW = 5
        private const val RELIABLE_WINDOW = 15
        private const val DEFINITIVE_WINDOW = 30

        // Gürültü bandı (cihaz takılı değilken gözlemlenen)
        private const val NOISE_BAND_LOW = 140
        private const val NOISE_BAND_HIGH = 170

        // Fizyolojik sınırlar
        private const val PHYSIOLOGICAL_MIN = 30
        private const val PHYSIOLOGICAL_MAX = 220

        // Sample Entropy parametreleri
        private const val ENTROPY_M = 2
        private const val ENTROPY_R_FACTOR = 0.2
        private const val ENTROPY_R_MIN = 5.0 // Düşük std koruması

        // Karar eşikleri
        private const val NOISE_BAND_THRESHOLD_HIGH = 0.80f  // %80+ = kesin gürültü
        private const val NOISE_BAND_THRESHOLD_MEDIUM = 0.60f // %60+ = şüpheli
        private const val ENTROPY_NOISE_THRESHOLD = 0.3 // Düşük entropy = gürültü
        private const val ENTROPY_AFIB_THRESHOLD = 1.5  // Yüksek entropy = AFib olabilir
    }

    // Thread-safe HR buffer
    private val hrBuffer = ConcurrentLinkedQueue<TimestampedHR>()

    // Son kalite skoru
    private var lastQualityResult: QualityResult = QualityResult(
        quality = 0f,
        confidence = 0f,
        stage = DecisionStage.UNKNOWN,
        reason = "Yeterli veri yok"
    )

    data class TimestampedHR(
        val timestamp: Long,
        val heartRate: Int
    )

    data class QualityResult(
        val quality: Float,      // 0.0 (gürültü) - 1.0 (temiz sinyal)
        val confidence: Float,   // Kararın güvenilirliği
        val stage: DecisionStage,
        val reason: String,
        val isNoise: Boolean = quality < 0.5f,
        val entropy: Double = 0.0,
        val noiseBandDensity: Float = 0f
    )

    enum class DecisionStage(val windowSize: Int, val baseConfidence: Float) {
        UNKNOWN(0, 0f),
        QUICK(QUICK_WINDOW, 0.6f),
        RELIABLE(RELIABLE_WINDOW, 0.8f),
        DEFINITIVE(DEFINITIVE_WINDOW, 0.95f)
    }

    /**
     * Yeni HR değeri ekle ve kalite analizi yap
     */
    @Synchronized
    fun addReading(heartRate: Int): QualityResult {
        val now = System.currentTimeMillis()

        // Buffer'a ekle
        hrBuffer.add(TimestampedHR(now, heartRate))

        // Eski verileri temizle (30 saniyeden eski)
        val cutoff = now - (DEFINITIVE_WINDOW * 1000L)
        while (hrBuffer.peek()?.timestamp?.let { it < cutoff } == true) {
            hrBuffer.poll()
        }

        // Analiz yap
        lastQualityResult = analyze()

        Log.d(TAG, "HR=$heartRate, Quality=${String.format("%.2f", lastQualityResult.quality)}, " +
                "Stage=${lastQualityResult.stage}, Noise=${lastQualityResult.isNoise}, " +
                "Reason=${lastQualityResult.reason}")

        return lastQualityResult
    }

    /**
     * Ana analiz fonksiyonu
     */
    private fun analyze(): QualityResult {
        val samples = hrBuffer.map { it.heartRate }
        val size = samples.size

        return when {
            size >= DEFINITIVE_WINDOW -> definitiveAnalysis(samples)
            size >= RELIABLE_WINDOW -> reliableAnalysis(samples)
            size >= QUICK_WINDOW -> quickAnalysis(samples)
            else -> QualityResult(
                quality = 0.5f,
                confidence = 0f,
                stage = DecisionStage.UNKNOWN,
                reason = "Yeterli veri yok (${size}/${QUICK_WINDOW})"
            )
        }
    }

    /**
     * 5 saniye hızlı analiz - sadece band yoğunluğu
     */
    private fun quickAnalysis(samples: List<Int>): QualityResult {
        val noiseBandDensity = calculateNoiseBandDensity(samples)

        // Fizyolojik aralık dışı kontrolü
        val outsidePhysiological = samples.count { it !in PHYSIOLOGICAL_MIN..PHYSIOLOGICAL_MAX }
        if (outsidePhysiological > samples.size * 0.3) {
            return QualityResult(
                quality = 0.1f,
                confidence = 0.7f,
                stage = DecisionStage.QUICK,
                reason = "Fizyolojik aralık dışı değerler",
                noiseBandDensity = noiseBandDensity
            )
        }

        // Yüksek gürültü bandı yoğunluğu = gürültü
        if (noiseBandDensity > NOISE_BAND_THRESHOLD_HIGH) {
            return QualityResult(
                quality = 0.2f,
                confidence = 0.6f,
                stage = DecisionStage.QUICK,
                reason = "Gürültü bandında yüksek yoğunluk (${(noiseBandDensity * 100).toInt()}%)",
                noiseBandDensity = noiseBandDensity
            )
        }

        // Şüpheli ama kesin değil
        if (noiseBandDensity > NOISE_BAND_THRESHOLD_MEDIUM) {
            return QualityResult(
                quality = 0.5f,
                confidence = 0.5f,
                stage = DecisionStage.QUICK,
                reason = "Şüpheli sinyal, analiz devam ediyor",
                noiseBandDensity = noiseBandDensity
            )
        }

        // Muhtemelen temiz
        return QualityResult(
            quality = 0.8f,
            confidence = 0.6f,
            stage = DecisionStage.QUICK,
            reason = "Ön analiz: Temiz görünüyor",
            noiseBandDensity = noiseBandDensity
        )
    }

    /**
     * 15 saniye güvenilir analiz - band yoğunluğu + entropy
     */
    private fun reliableAnalysis(samples: List<Int>): QualityResult {
        val noiseBandDensity = calculateNoiseBandDensity(samples)
        val entropy = calculateSampleEntropy(samples)
        val normalizedEntropy = normalizeEntropy(entropy)

        Log.d(TAG, "Reliable: noiseBand=${(noiseBandDensity * 100).toInt()}%, entropy=${String.format("%.3f", entropy)}")

        // DURUM 1: Yüksek band yoğunluğu + düşük entropy = KESİN GÜRÜLTÜ
        if (noiseBandDensity > NOISE_BAND_THRESHOLD_HIGH && entropy < ENTROPY_NOISE_THRESHOLD) {
            return QualityResult(
                quality = 0.1f,
                confidence = 0.85f,
                stage = DecisionStage.RELIABLE,
                reason = "Gürültü tespit edildi (dar band, düşük entropy)",
                entropy = entropy,
                noiseBandDensity = noiseBandDensity
            )
        }

        // DURUM 2: Yüksek band yoğunluğu + YÜKSEK entropy = AFib OLABİLİR (KORU!)
        if (noiseBandDensity > NOISE_BAND_THRESHOLD_MEDIUM && entropy > ENTROPY_AFIB_THRESHOLD) {
            return QualityResult(
                quality = 0.7f,  // Düşük güvenle kabul et
                confidence = 0.6f,
                stage = DecisionStage.RELIABLE,
                reason = "AFib olabilir - yüksek entropy (${String.format("%.2f", entropy)})",
                entropy = entropy,
                noiseBandDensity = noiseBandDensity
            )
        }

        // DURUM 3: Düşük band yoğunluğu = muhtemelen temiz
        if (noiseBandDensity < NOISE_BAND_THRESHOLD_MEDIUM) {
            return QualityResult(
                quality = 0.9f,
                confidence = 0.8f,
                stage = DecisionStage.RELIABLE,
                reason = "Temiz sinyal",
                entropy = entropy,
                noiseBandDensity = noiseBandDensity
            )
        }

        // DURUM 4: Belirsiz - orta kalite
        val quality = 0.5f + (normalizedEntropy * 0.3f) - (noiseBandDensity * 0.2f)
        return QualityResult(
            quality = quality.coerceIn(0.3f, 0.8f),
            confidence = 0.7f,
            stage = DecisionStage.RELIABLE,
            reason = "Analiz devam ediyor",
            entropy = entropy,
            noiseBandDensity = noiseBandDensity
        )
    }

    /**
     * 30 saniye kesin analiz - tam özellik seti
     */
    private fun definitiveAnalysis(samples: List<Int>): QualityResult {
        val noiseBandDensity = calculateNoiseBandDensity(samples)
        val entropy = calculateSampleEntropy(samples)
        val std = samples.standardDeviation()
        val mean = samples.average()
        val trend = calculateTrend(samples)

        Log.d(TAG, "Definitive: band=${(noiseBandDensity * 100).toInt()}%, " +
                "entropy=${String.format("%.3f", entropy)}, std=${String.format("%.1f", std)}, " +
                "mean=${String.format("%.1f", mean)}, trend=${String.format("%.3f", trend)}")

        // KESİN GÜRÜLTÜ: Yüksek band + düşük entropy + trend yok
        if (noiseBandDensity > NOISE_BAND_THRESHOLD_HIGH &&
            entropy < ENTROPY_NOISE_THRESHOLD &&
            abs(trend) < 0.1) {
            return QualityResult(
                quality = 0.05f,
                confidence = 0.95f,
                stage = DecisionStage.DEFINITIVE,
                reason = "Gürültü: Dar band (${(noiseBandDensity * 100).toInt()}%), düşük entropy, trend yok",
                entropy = entropy,
                noiseBandDensity = noiseBandDensity
            )
        }

        // AFib KORUMA: Yüksek entropy = gerçek düzensizlik
        if (entropy > ENTROPY_AFIB_THRESHOLD) {
            // Ama fizyolojik sınırlar içinde olmalı
            val inPhysiological = samples.all { it in PHYSIOLOGICAL_MIN..PHYSIOLOGICAL_MAX }
            if (inPhysiological) {
                return QualityResult(
                    quality = 0.85f,
                    confidence = 0.9f,
                    stage = DecisionStage.DEFINITIVE,
                    reason = "Geçerli: Yüksek HRV/AFib paterni (entropy=${String.format("%.2f", entropy)})",
                    entropy = entropy,
                    noiseBandDensity = noiseBandDensity
                )
            }
        }

        // BRADİKARDİ/TAŞİKARDİ KORUMA
        if (mean < 50 || mean > 150) {
            // Düşük/yüksek HR ama stabil = gerçek
            if (std < 15 && entropy < 1.0) {
                return QualityResult(
                    quality = 0.9f,
                    confidence = 0.9f,
                    stage = DecisionStage.DEFINITIVE,
                    reason = "Geçerli: Stabil ${if (mean < 50) "bradikardi" else "taşikardi"} (${mean.toInt()} BPM)",
                    entropy = entropy,
                    noiseBandDensity = noiseBandDensity
                )
            }
        }

        // NORMAL TEMİZ SİNYAL
        if (noiseBandDensity < NOISE_BAND_THRESHOLD_MEDIUM && std < 30) {
            return QualityResult(
                quality = 0.95f,
                confidence = 0.95f,
                stage = DecisionStage.DEFINITIVE,
                reason = "Temiz sinyal",
                entropy = entropy,
                noiseBandDensity = noiseBandDensity
            )
        }

        // BELİRSİZ DURUM - şüpheyle kabul et (false positive'den kaçın)
        val quality = calculateFinalQuality(noiseBandDensity, entropy, std)
        return QualityResult(
            quality = quality,
            confidence = 0.8f,
            stage = DecisionStage.DEFINITIVE,
            reason = "Belirsiz - düşük güvenle kabul",
            entropy = entropy,
            noiseBandDensity = noiseBandDensity
        )
    }

    /**
     * 140-170 BPM gürültü bandındaki yoğunluk
     */
    private fun calculateNoiseBandDensity(samples: List<Int>): Float {
        if (samples.isEmpty()) return 0f
        val inBand = samples.count { it in NOISE_BAND_LOW..NOISE_BAND_HIGH }
        return inBand.toFloat() / samples.size
    }

    /**
     * Sample Entropy hesaplama
     * Gürültü = düşük entropy (dar bandda template'ler eşleşir)
     * AFib = yüksek entropy (geniş dağılım)
     */
    private fun calculateSampleEntropy(data: List<Int>): Double {
        if (data.size < ENTROPY_M + 2) return 0.0

        val std = data.standardDeviation()
        val r = max(ENTROPY_R_FACTOR * std, ENTROPY_R_MIN)

        val n = data.size
        var matchesM = 0
        var matchesM1 = 0

        // Template matching (m ve m+1 uzunluğunda)
        for (i in 0 until n - ENTROPY_M) {
            for (j in i + 1 until n - ENTROPY_M) {
                // m uzunluğunda eşleşme kontrolü
                var matchM = true
                for (k in 0 until ENTROPY_M) {
                    if (abs(data[i + k] - data[j + k]) > r) {
                        matchM = false
                        break
                    }
                }
                if (matchM) {
                    matchesM++
                    // m+1 uzunluğunda eşleşme kontrolü
                    if (i + ENTROPY_M < n && j + ENTROPY_M < n) {
                        if (abs(data[i + ENTROPY_M] - data[j + ENTROPY_M]) <= r) {
                            matchesM1++
                        }
                    }
                }
            }
        }

        return if (matchesM > 0 && matchesM1 > 0) {
            -ln(matchesM1.toDouble() / matchesM)
        } else {
            2.0 // Yüksek entropy (eşleşme yok)
        }
    }

    /**
     * Entropy normalizasyonu (0-1 arası)
     */
    private fun normalizeEntropy(entropy: Double): Float {
        // Tipik aralık: 0 (çok düzenli) - 2.5 (çok kaotik)
        return (entropy / 2.5).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Trend hesaplama (lineer regresyon eğimi)
     */
    private fun calculateTrend(samples: List<Int>): Double {
        if (samples.size < 2) return 0.0

        val n = samples.size
        val xMean = (n - 1) / 2.0
        val yMean = samples.average()

        var numerator = 0.0
        var denominator = 0.0

        for (i in samples.indices) {
            numerator += (i - xMean) * (samples[i] - yMean)
            denominator += (i - xMean) * (i - xMean)
        }

        return if (denominator > 0) numerator / denominator else 0.0
    }

    /**
     * Final kalite skoru hesaplama
     */
    private fun calculateFinalQuality(noiseBandDensity: Float, entropy: Double, std: Double): Float {
        val normalizedEntropy = normalizeEntropy(entropy)

        // Ağırlıklı skor (AFib-friendly: yüksek entropy = yüksek kalite)
        val entropyScore = normalizedEntropy * 0.4f
        val bandScore = (1f - noiseBandDensity) * 0.4f
        val stabilityScore = (1f - (std / 50.0).coerceIn(0.0, 1.0).toFloat()) * 0.2f

        return (entropyScore + bandScore + stabilityScore).coerceIn(0.3f, 0.9f)
    }

    /**
     * Buffer'ı temizle
     */
    fun reset() {
        hrBuffer.clear()
        lastQualityResult = QualityResult(
            quality = 0f,
            confidence = 0f,
            stage = DecisionStage.UNKNOWN,
            reason = "Reset"
        )
    }

    /**
     * Son kalite sonucunu al
     */
    fun getLastResult(): QualityResult = lastQualityResult

    /**
     * Buffer'daki örnek sayısı
     */
    fun getSampleCount(): Int = hrBuffer.size
}

// Extension functions
private fun List<Int>.standardDeviation(): Double {
    if (size < 2) return 0.0
    val mean = average()
    val variance = sumOf { (it - mean) * (it - mean) } / (size - 1)
    return sqrt(variance)
}
