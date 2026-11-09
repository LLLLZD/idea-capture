@file:OptIn(KoinInternalApi::class)

package top.lzdrichard.idea.di

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertTrue
import org.koin.core.annotation.KoinInternalApi
import top.lzdrichard.idea.data.AuthRepository
import top.lzdrichard.idea.data.ConfigRepository
import top.lzdrichard.idea.data.PendingQueue
import top.lzdrichard.idea.data.RecordRepository
import top.lzdrichard.idea.data.RecordingController
import top.lzdrichard.idea.presentation.AppViewModel
import top.lzdrichard.idea.presentation.feature.home.HomeViewModel
import top.lzdrichard.idea.presentation.feature.login.LoginViewModel

/**
 * 装配点完整性测试 —— 这类断裂只有 App 启动才会暴露。
 *
 * 做法照搬 LoostoneCenter：读 Koin 的模块登记表（Module.mappings）拿类型 KClass，
 * 只读不解析，不创建实例、不 startKoin、不引 koin-test。
 *
 * 约定：新增一个「靠注入活着」的仓储/页面状态，就来这里加一行；
 * 装配点忘了登记，本测试直接红。
 */
class KoinAssemblyTest {

    /** 装配点必须登记的仓储/网关（类型 → 谁靠它活着） */
    private val contractTypes: Map<KClass<*>, String> = mapOf(
        ConfigRepository::class to "下发客户配置与体裁列表，登录后立刻刷新",
        AuthRepository::class to "登录态与恢复会话，所有页面读它",
        RecordRepository::class to "列表/详情/重试，首页与详情页都依赖",
        RecordingController::class to "录音→转写→提交的唯一入口",
        PendingQueue::class to "断网排队与补送，首页排队计数依赖",
    )

    /** 启动时会被解析的具体类型：注册被误删同样是启动崩溃 */
    private val rootTypes: List<KClass<*>> = listOf(
        AppViewModel::class,
        LoginViewModel::class,
        HomeViewModel::class,
    )

    /** 装配点登记过的全部类型（主类型 + bind 的副类型） */
    private val declaredTypes: Set<KClass<*>> =
        (sharedModule.mappings.values + platformModule.mappings.values)
            .flatMap { factory ->
                val definition = factory.beanDefinition
                listOf(definition.primaryType) + definition.secondaryTypes
            }
            .toSet()

    @Test
    fun everyRepositoryIsBoundAtAssemblyPoint() {
        val missing = contractTypes.filterKeys { it !in declaredTypes }
        assertTrue(
            missing.isEmpty(),
            "装配点缺少绑定：" + missing.map { (type, why) -> type.simpleName + "（" + why + "）" } +
                "。当前登记表共 " + declaredTypes.size + " 个类型，若为 0 说明 Koin 内部 API 变了、本测试已失效。" +
                " —— 这种漏绑编译期看不出来，启动必崩",
        )
    }

    @Test
    fun startupGraphRootsAreDeclared() {
        val missing = rootTypes.filter { it !in declaredTypes }
        assertTrue(
            missing.isEmpty(),
            "装配点不再注册启动依赖：" + missing.map { it.simpleName } +
                "。当前登记表共 " + declaredTypes.size + " 个类型。",
        )
    }
}
