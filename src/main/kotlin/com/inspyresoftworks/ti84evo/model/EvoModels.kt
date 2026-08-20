package com.inspyresoftworks.ti84evo.model

/**
 * Models exposed to the PyCharm UI.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
data class EvoScreenCapture(
    val width: Int,
    val height: Int,
    val bitsPerPixel: Int,
    val framebuffer: ByteArray,
    val metadata: Map<String, Any?>,
)
