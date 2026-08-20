package com.inspyresoftworks.ti84evo.python

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

/** Locates the type declarations packaged inside the plugin. */
internal object TiPythonStubs {
    const val RESOURCE_ROOT = "python-stubs"

    val moduleNames = setOf(
        "ti_draw",
        "ti_hub",
        "ti_image",
        "ti_plotlib",
        "ti_rover",
        "ti_system",
    )

    val root: VirtualFile?
        get() {
            val resource = javaClass.classLoader.getResource(RESOURCE_ROOT) ?: return null
            return VfsUtil.findFileByURL(resource)
        }
}
