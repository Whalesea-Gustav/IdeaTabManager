package com.whalesea.ideatabmanager

import com.intellij.AbstractBundle
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

private const val BUNDLE = "messages.IdeaTabManagerBundle"

object IdeaTabManagerBundle : AbstractBundle(BUNDLE) {
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)

    @JvmStatic
    fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): Supplier<String> =
        getLazyMessage(key, *params)
}
