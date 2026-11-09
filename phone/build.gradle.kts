plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

// ── 主题 token 门禁：任何编译/打包前先过 scripts/token-guard.mjs，违规即中断构建 ──
// 照搬 D:/LoostoneCenter/build.gradle.kts：只扫 UI 所在模块 shared/src（其他模块无 UI）；
// 逃生阀：-PskipTokenGuard 或 -x tokenGuard
val guardReport = layout.buildDirectory.file("token-guard-report.txt")
val tokenGuard by tasks.registering(Exec::class) {
    group = "verification"
    description = "扫描 shared/src 硬编码色/圆角/字号，违规即构建失败（报错含可跳转的文件:行号）"
    workingDir = rootDir
    commandLine("node", "scripts/token-guard.mjs", "shared/src", "--report", guardReport.get().asFile.absolutePath)
    isIgnoreExitValue = true          // 自行接管失败：把完整报告塞进 "What went wrong"
    outputs.upToDateWhen { false }
    onlyIf { !gradle.startParameter.projectProperties.containsKey("skipTokenGuard") }
    doLast {
        if (executionResult.get().exitValue != 0) {
            val r = guardReport.get().asFile
            throw GradleException(r.takeIf { it.exists() }?.readText()?.trim() ?: "token-guard 失败")
        }
    }
}

subprojects {
    tasks.matching { it.name.startsWith("compile") || it.name.startsWith("assemble") }
        .configureEach { dependsOn(tokenGuard) }
}
