package top.lzdrichard.feature.asr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 路由规则的回归用例：Auto 原生优先、不可用与失败都降级；Native 只走原生；Api 只走 api。 */
class AsrServiceTest {

    private class FakeEngine(
        override val provider: String,
        override val model: String,
        private val available: Boolean = true,
        private val result: String = "原生文字",
        private val boom: Boolean = false,
    ) : AsrEngine {
        var calls = 0
            private set

        override suspend fun isAvailable(): Boolean = available

        override suspend fun transcribe(audio: AsrAudio): String {
            calls += 1
            if (boom) throw AsrException("识别失败")
            return result
        }
    }

    private fun service(
        native: AsrEngine = FakeEngine("native", "sf-speech"),
        api: AsrEngine = FakeEngine("funasr", "fun-asr-nano", result = "在线文字"),
    ) = AsrService(native, api)

    @Test
    fun autoPrefersNativeWhenAvailable() = kotlinx.coroutines.test.runTest {
        val native = FakeEngine("native", "sf-speech")
        val api = FakeEngine("funasr", "fun-asr-nano", result = "在线文字")
        val outcome = AsrService(native, api).transcribe(AsrAudio("/tmp/a.m4a"))
        assertEquals("原生文字", outcome.text)
        assertEquals(AsrRoute.Native, outcome.used)
        assertFalse(outcome.degraded)
        assertEquals(0, api.calls)
    }

    @Test
    fun autoFallsBackWhenNativeUnavailable() = kotlinx.coroutines.test.runTest {
        val api = FakeEngine("funasr", "fun-asr-nano", result = "在线文字")
        val outcome = AsrService(FakeEngine("native", "sf-speech", available = false), api)
            .transcribe(AsrAudio("/tmp/a.m4a"))
        assertEquals("在线文字", outcome.text)
        assertEquals(AsrRoute.Api, outcome.used)
        assertTrue(outcome.degraded)
        assertEquals("funasr", outcome.provider)
        assertEquals("fun-asr-nano", outcome.model)
    }

    @Test
    fun autoFallsBackWhenNativeThrows() = kotlinx.coroutines.test.runTest {
        val api = FakeEngine("funasr", "fun-asr-nano", result = "在线文字")
        val outcome = AsrService(FakeEngine("native", "sf-speech", boom = true), api)
            .transcribe(AsrAudio("/tmp/a.m4a"))
        assertEquals("在线文字", outcome.text)
        assertTrue(outcome.degraded)
    }

    @Test
    fun emptyNativeResultIsNotAFailure() = kotlinx.coroutines.test.runTest {
        val api = FakeEngine("funasr", "fun-asr-nano")
        val outcome = AsrService(FakeEngine("native", "sf-speech", result = ""), api)
            .transcribe(AsrAudio("/tmp/a.m4a"))
        assertEquals("", outcome.text)
        assertEquals(AsrRoute.Native, outcome.used)
        assertFalse(outcome.degraded)
        assertEquals(0, api.calls)
    }

    @Test
    fun nativeRouteNeverTouchesApi() = runTestBlocking {
        val native = FakeEngine("native", "sf-speech", available = false)
        val api = FakeEngine("funasr", "fun-asr-nano")
        assertFailsWith<AsrUnavailableException> {
            AsrService(native, api).transcribe(AsrAudio("/tmp/a.m4a"), AsrRoute.Native)
        }
        assertEquals(0, api.calls)
    }

    @Test
    fun apiRouteNeverTouchesNative() = kotlinx.coroutines.test.runTest {
        val native = FakeEngine("native", "sf-speech")
        val api = FakeEngine("funasr", "fun-asr-nano", result = "在线文字")
        val outcome = AsrService(native, api).transcribe(AsrAudio("/tmp/a.m4a"), AsrRoute.Api)
        assertEquals(AsrRoute.Api, outcome.used)
        assertEquals(0, native.calls)
    }
}

private fun runTestBlocking(block: suspend () -> Unit) =
    kotlinx.coroutines.runBlocking { block() }
