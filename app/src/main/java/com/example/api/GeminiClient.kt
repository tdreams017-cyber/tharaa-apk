package com.example.api

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val SYSTEM_PROMPT = """
        أنت مستشار الثراء التجاري الذكي الخبير، ومهمتك هي مساعدة المستخدمين في فهم وتطبيق مبادئ كتاب "طريقك نحو الثراء (من الصفر إلى الاحتراف)" للكاتب صادق الخفاجي.
        
        استخدم القواعد التالية دائماً في ردودك:
        1. تحدث بلهجة عربية مهنية، ملهمة، واضحة ومبسطة جداً. تجنب التعقيد الأكاديمي.
        2. عند استشارة فكرة تجارية، استخدم المفاهيم التالية من الكتاب لتوجيه المستخدم:
           - عقلية الثراء (الأصول مقابل الخصوم)
           - نظرية الفجوة لإيجاد الأفكار وحل المشاكل الصغيرة للناس
           - معادلة الجذب (Hook-Story-Offer) ومفهوم بيع النتيجة بدلاً من بيع المنتج
           - أتمتة الأنظمة وبناء علامة تجارية قوية لرفع القيمة (Branding)
           - بدء مشاريع الوساطة ودروب سيرفيسينج (Drop Servicing) أو التسويق بالعمولة بدون رأس مال
           - حساب صافي الربح الدقيق (سعر البيع - التكاليف) وإعادة الاستثمار
        3. كن عملياً واقترح خطة واضحة ومدروسة كالتالي: (الخطوة الأولى اليوم، الخطوة الثانية الأسبوع القادم، الخطوة الثالثة الشهر القادم).
        4. شجع دائماً على اتخاذ خطوات سريعة وتجريب السوق بأقل تكلفة ممكنة (MVP).
        5. لا تذكر تفاصيل تقنية برمجية عن التطبيق بل ركز كلياً على الجانب التجاري وصناعة الأرباح وحل مشاكل السوق.
    """

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Helper to escape JSON strings simply
    private fun escapeJsonString(string: String): String {
        val builder = StringBuilder()
        for (element in string) {
            when (element) {
                '\\' -> builder.append("\\\\")
                '\"' -> builder.append("\\\"")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> builder.append(element)
            }
        }
        return builder.toString()
    }

    private fun getSecretValue(fieldName: String): String {
        return try {
            val buildConfigClass = Class.forName("com.example.BuildConfig")
            val field = buildConfigClass.getField(fieldName)
            val value = field.get(null) as? String ?: ""
            if (value.isNotBlank()) value else ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun getApiKey(): String {
        var key = getSecretValue("GEMINI_API_KEY")
        if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
            key = getSecretValue("API_KEY")
        }
        return key
    }

    suspend fun askCoach(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "MY_API_KEY") {
            return@withContext "API_KEY_MISSING"
        }

        val escapedPrompt = escapeJsonString(prompt)
        val escapedSystemPrompt = escapeJsonString(SYSTEM_PROMPT)

        // Directly construct the JSON payload
        val jsonPayload = """
            {
                "contents": [{
                    "parts": [{"text": "$escapedPrompt"}]
                }],
                "systemInstruction": {
                    "parts": [{"text": "$escapedSystemPrompt"}]
                },
                "generationConfig": {
                    "temperature": 0.7
                }
            }
        """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonPayload.toRequestBody(mediaType)

        // Try multiple models to ensure robust connection on any API key configuration
        val modelsToTry = listOf("gemini-1.5-flash", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-3.5-flash")
        var lastErrorResponse = ""

        for (modelName in modelsToTry) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            try {
                val response = client.newCall(request).execute()
                val bodyString = response.body?.string() ?: ""
                
                if (response.isSuccessful && bodyString.isNotEmpty()) {
                    val jsonResponse = JSONObject(bodyString)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        if (content != null) {
                            val parts = content.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                return@withContext parts.getJSONObject(0).optString("text", "لا توجد استجابة.")
                            }
                        }
                    }
                } else {
                    lastErrorResponse = "Model $modelName error ${response.code}: $bodyString"
                }
            } catch (e: Exception) {
                lastErrorResponse = "Model $modelName exception: ${e.localizedMessage}"
            }
        }

        return@withContext "عذراً، حدث خطأ أثناء محاولة الاتصال بالذكاء الاصطناعي:\n$lastErrorResponse"
    }
}
