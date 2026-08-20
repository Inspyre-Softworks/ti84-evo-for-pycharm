package com.inspyresoftworks.ti84evo.python

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull

class TiPythonStubsTest {
    @Test
    fun `bundles the complete Evo ti module surface`() {
        val expectedFunctions = mapOf(
            "ti_draw.pyi" to listOf(
                "clear", "clear_rect", "draw_circle", "draw_line", "draw_poly",
                "draw_rect", "draw_text", "fill_circle", "fill_poly", "fill_rect",
                "get_screen_dim", "plot_xy", "poly_xy", "set_color", "set_pen", "set_window",
                "show_draw",
            ),
            "ti_hub.pyi" to listOf(
                "connect", "disconnect", "set", "read", "calibrate", "range",
                "version", "begin", "start", "about", "isti", "what", "who",
                "last_error", "sleep", "wait", "get", "send",
            ),
            "ti_image.pyi" to listOf(
                "load_image", "show_image", "clear_image", "get_pixel",
                "set_pixel", "show_screen",
            ),
            "ti_plotlib.pyi" to listOf(
                "cls", "grid", "window", "auto_window", "axes", "labels",
                "title", "show_plot", "use_buffer", "color", "colour",
                "scatter", "plot", "line", "lin_reg", "pen", "text_at",
            ),
            "ti_rover.pyi" to listOf(
                "forward", "backward", "left", "right", "stop", "stop_clear",
                "resume", "stay", "to_xy", "to_polar", "to_angle",
                "forward_time", "backward_time", "ranger_measurement",
                "color_measurement", "encoders_gyro_measurement", "color_rgb",
                "motors", "waypoint_xythdrn", "path_done", "wait_until_done",
                "position", "grid_origin", "path_clear", "zero_gyro",
            ),
            "ti_system.pyi" to listOf(
                "disp_cursor", "disp_at", "disp_clr", "disp_wait", "escape", "get_key",
                "recall_RegEQ", "recall_list", "sleep", "store_list", "wait",
                "wait_key",
            ),
        )

        kotlin.test.assertEquals(
            TiPythonStubs.moduleNames,
            expectedFunctions.keys.mapTo(mutableSetOf()) { it.removeSuffix(".pyi") },
        )

        for ((fileName, functions) in expectedFunctions) {
            val resourceName = "${TiPythonStubs.RESOURCE_ROOT}/$fileName"
            val resource = assertNotNull(javaClass.classLoader.getResource(resourceName), resourceName)
            val declarations = resource.readText()
            for (function in functions) {
                assertContains(
                    declarations,
                    "def $function(",
                    message = "$function should be offered by $fileName",
                )
            }
        }
    }
}
