package com.example.routerspeed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

class RouterException(
    message: String,
    cause: Throwable? = null,
    val httpStatus: Int? = null,
) : Exception(message, cause) {
    val isSessionExpired: Boolean
        get() = httpStatus == 401 || httpStatus == 403 || httpStatus == 404
}

class RouterRepository {
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val client = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var sessionBaseUrl: HttpUrl? = null

    suspend fun login(routerAddress: String, password: String) = withContext(Dispatchers.IO) {
        val baseUrl = parseBaseUrl(routerAddress)
        cookieManager.cookieStore.removeAll()

        val indexUrl = baseUrl.resolve("/html/index.html")
            ?: throw RouterException("路由器地址无效")
        val html = execute(Request.Builder().url(indexUrl).get().build())
        var csrf = extractCsrf(html)

        val firstNonce = Scram.randomNonce()
        val nonceData = JSONObject()
            .put("username", "admin")
            .put("firstnonce", firstNonce)
        val nonceResponse = postJson(baseUrl, "/api/system/user_login_nonce", nonceData, csrf)
        ensureSuccess(nonceResponse, "登录初始化失败")

        val salt = nonceResponse.requiredString("salt")
        val iterations = nonceResponse.optInt("iterations", -1)
        if (iterations <= 0) throw RouterException("路由器返回了无效的加密参数")
        val finalNonce = nonceResponse.requiredString("servernonce")
        val proof = Scram.clientProof(password, salt, iterations, firstNonce, finalNonce)

        csrf = Csrf(
            nonceResponse.requiredString("csrf_param"),
            nonceResponse.requiredString("csrf_token"),
        )
        val proofData = JSONObject()
            .put("clientproof", proof)
            .put("finalnonce", finalNonce)
        val proofResponse = postJson(baseUrl, "/api/system/user_login_proof", proofData, csrf)
        ensureSuccess(proofResponse, "用户名或密码错误")

        sessionBaseUrl = baseUrl
    }

    suspend fun fetchDevices(): List<Device> = withContext(Dispatchers.IO) {
        val baseUrl = sessionBaseUrl ?: throw RouterException("请先登录路由器")
        val url = baseUrl.resolve("/api/system/HostInfo")
            ?: throw RouterException("路由器地址无效")
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Referer", baseUrl.resolve("/html/index.html").toString())
            .get()
            .build()
        val body = execute(request)
        if (body.isBlank()) {
            throw RouterException("未获取到设备数据，登录可能已失效，请重新登录")
        }
        try {
            val hosts = JSONArray(body)
            buildList {
                for (index in 0 until hosts.length()) {
                    val host = hosts.getJSONObject(index)
                    add(
                        Device(
                            id = host.optString("ID"),
                            hostName = host.optString("HostName"),
                            actualName = host.optString("ActualName"),
                            ipAddress = host.optString("IPAddress"),
                            macAddress = host.optString("MACAddress"),
                            active = host.optBoolean("Active"),
                            interfaceType = host.optString("InterfaceType"),
                            frequency = host.optString("Frequency"),
                            upRate = host.optLong("UpRate"),
                            downRate = host.optLong("DownRate"),
                            txKBytes = host.optLong("TxKBytes"),
                            rxKBytes = host.optLong("RxKBytes"),
                        )
                    )
                }
            }
        } catch (error: JSONException) {
            throw RouterException("设备数据格式无法识别，登录可能已失效", error)
        }
    }

    fun logout() {
        sessionBaseUrl = null
        cookieManager.cookieStore.removeAll()
    }

    private fun postJson(
        baseUrl: HttpUrl,
        path: String,
        data: JSONObject,
        csrf: Csrf,
    ): JSONObject {
        val payload = JSONObject()
            .put("data", data)
            .put(
                "csrf",
                JSONObject()
                    .put("csrf_param", csrf.param)
                    .put("csrf_token", csrf.token),
            )
        val url = baseUrl.resolve(path) ?: throw RouterException("路由器地址无效")
        val request = Request.Builder()
            .url(url)
            .header("_responseformat", "JSON")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", baseUrl.resolve("/html/index.html").toString())
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return try {
            JSONObject(execute(request))
        } catch (error: JSONException) {
            throw RouterException("路由器登录响应格式无法识别", error)
        }
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = if (response.code == 401 || response.code == 403) {
                    "路由器拒绝了请求，请重新登录"
                } else {
                    "路由器返回 HTTP ${response.code}"
                }
                throw RouterException(message, httpStatus = response.code)
            }
            return body
        }
    }

    private fun parseBaseUrl(address: String): HttpUrl {
        val input = address.trim()
        if (input.isEmpty()) throw RouterException("请输入路由器地址")
        val candidate = if (input.startsWith("http://") || input.startsWith("https://")) {
            input
        } else {
            "http://$input"
        }
        return candidate.toHttpUrlOrNull() ?: throw RouterException("路由器地址格式无效")
    }

    private fun extractCsrf(html: String): Csrf {
        fun value(name: String): String {
            val assignment = Regex("""\b$name\s*=\s*['"]([^'"]+)['"]""")
            val meta = Regex(
                """<meta[^>]*name=['"]$name['"][^>]*content=['"]([^'"]+)['"]""",
                RegexOption.IGNORE_CASE,
            )
            return (assignment.find(html) ?: meta.find(html))?.groupValues?.get(1)
                ?: throw RouterException("无法从路由器页面获取登录令牌")
        }
        return Csrf(value("csrf_param"), value("csrf_token"))
    }

    private fun ensureSuccess(response: JSONObject, operation: String) {
        val errorCode = response.optInt("err", -1)
        if (errorCode == 0) return
        if (errorCode == PASSWORD_ERROR_CODE) throw RouterException("路由器密码不正确")
        val serverMessage = response.optString("message").ifBlank { response.optString("errmsg") }
        val message = if (serverMessage.isNotBlank()) {
            "$operation：$serverMessage（错误码 $errorCode）"
        } else {
            "$operation（错误码 $errorCode）"
        }
        throw RouterException(message)
    }

    private fun JSONObject.requiredString(name: String): String {
        val value = optString(name)
        if (value.isEmpty()) throw RouterException("路由器登录响应缺少 $name")
        return value
    }

    private data class Csrf(val param: String, val token: String)

    private companion object {
        const val PASSWORD_ERROR_CODE = 4784230
        val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
    }
}
