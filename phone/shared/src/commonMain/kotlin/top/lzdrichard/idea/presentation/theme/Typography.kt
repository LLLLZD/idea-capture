package top.lzdrichard.idea.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 应用 Typography 定义（结构照搬 LoostoneCenter：字号 5 档归并，行高逐档固定）
 *
 * 五档实值：10 / 12 / 14 / 16 / 20（sp）
 * - 10 → labelSmall（极小注释，如排队计数）
 * - 12 → labelMedium / bodySmall（状态标签、说明）
 * - 14 → body 全线 + labelLarge（正文、按钮、列表项）
 * - 16 → titleSmall / titleMedium（区块标题）
 * - 20 → titleLarge / headlineSmall（页面大标题）
 *
 * 所有页面/组件的文字样式必须从 MaterialTheme.typography 获取，
 * 禁止硬编码 fontSize/TextStyle；特殊字重用 .copy(fontWeight=) 派生，不许改字号档。
 */
val AppTypography = Typography(
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp),

    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),

    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),

    titleSmall = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),

    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Normal, lineHeight = 28.sp),
)
