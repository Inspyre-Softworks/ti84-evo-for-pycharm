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

data class EvoDirectoryEntry(
    val name: String,
    val type: Int,
    val size: Long,
    val archived: Boolean,
    val tokenName: ByteArray,
) {
    val typeName: String
        get() = when (type) {
            0 -> "Number"
            1 -> "List"
            2 -> "Program"
            3 -> "Graph Database"
            4 -> "Picture"
            5 -> "Image"
            6 -> "Matrix"
            7 -> "Function"
            8 -> "AppVar"
            9 -> "Protected Program"
            10 -> "String"
            11 -> "Group"
            12 -> "Window"
            13 -> "Recall Window"
            14 -> "Table Setup"
            15 -> "Python Program"
            else -> "Type $type"
        }

    val location: String
        get() = if (archived) "Archive" else "RAM"
}
