package com.tencent.kuiklybase

/**
 * WebView 内需要屏蔽原生触摸的区域，单位与页面布局一致（dp / pt / vp）。
 *
 * 坐标为区域左上角与宽高；[align] 用于把区域锚定到 WebView 的四边，
 * 避免业务方依赖 WebView 的实时尺寸计算底部或右侧位置。
 */
data class TouchShieldRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val align: TouchShieldAlign = TouchShieldAlign.TOP_LEFT,
) {
    /** 序列化为跨端 prop 字符串：align,x,y,width,height */
    internal fun serialize(): String = listOf(
        align.value.toString(),
        x.toString(),
        y.toString(),
        width.toString(),
        height.toString(),
    ).joinToString(",")

    internal companion object {
        fun bottomBand(width: Float, height: Float): TouchShieldRect = TouchShieldRect(
            width = width,
            height = height,
            align = TouchShieldAlign.BOTTOM_STRETCH,
        )
    }
}

enum class TouchShieldAlign(val value: Int) {
    /** x/y 从左上角计算，width/height 为明确尺寸 */
    TOP_LEFT(0),
    /** x/y 从右上角计算，通常用于右侧面板 */
    TOP_RIGHT(1),
    /** x/y 从左下角计算，通常用于左下悬浮按钮 */
    BOTTOM_LEFT(2),
    /** x/y 从右下角计算，通常用于右下悬浮按钮 */
    BOTTOM_RIGHT(3),
    /** 贴底并横向铺满；x 作为左右 inset，height 必须为正 */
    BOTTOM_STRETCH(4),
    /** 贴顶并横向铺满；x 作为左右 inset，height 必须为正 */
    TOP_STRETCH(5),
    /** 贴左并纵向铺满；y 作为上下 inset，width 必须为正 */
    LEFT_STRETCH(6),
    /** 贴右并纵向铺满；y 作为上下 inset，width 必须为正 */
    RIGHT_STRETCH(7),
}
