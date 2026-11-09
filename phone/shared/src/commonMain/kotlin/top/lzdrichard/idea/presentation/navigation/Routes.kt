package top.lzdrichard.idea.presentation.navigation

/** 全部路由集中在这里，页面之间不写字符串字面量。 */
object Routes {
    const val LOGIN = "login"
    const val TEMPLATES = "templates"
    const val HOME = "home"
    const val DETAIL = "detail/{clientId}"
    const val DETAIL_ARG = "clientId"

    fun detail(clientId: String): String = "detail/" + clientId
}
