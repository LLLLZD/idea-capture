package top.lzdrichard.idea.data

/** 稿子相关的服务端动作（提交、列表、详情、重试），界面与 ViewModel 只跟它打交道。 */
class RecordRepository(private val api: Api) {

    suspend fun submit(
        clientId: String,
        text: String,
        durationMs: Int,
        provider: String?,
        model: String?,
    ): String = api.submit(clientId, text, durationMs, provider, model)

    suspend fun list(limit: Int = 20): List<RecordItem> = api.records(limit)

    suspend fun detail(clientId: String): RecordDetail = api.record(clientId)

    suspend fun retry(clientId: String) = api.retry(clientId)

    suspend fun statuses(clientIds: List<String>): List<RecordStatus> = api.statuses(clientIds)

    suspend fun reportRenderError(clientId: String, detail: String) = api.renderError(clientId, detail)
}
