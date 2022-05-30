// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


/**
 * Property delegate to enforce [AutoCloseable.close] being called when overwriting a previous value.
 * This ensures native resources cleanup in any circumstances when storing [AutoCloseable] objects
 * as member variables.
 *
 * ```kotlin
 * private var myAutoCloseable by autoCloseable<MyAutoCloseable>(null) { oldValue ->
 *     // oldValue (if not null) can be used if needed
 * }
 *
 * private var myOtherAutoCloseable by autoCloseable<MyAutoCloseable>(MyAutoCloseable())
 *
 * fun doSomething() {
 *   myAutoCloseable = MyAutoCloseable()
 *   myOtherAutoCloseable = null // ensures previous value is closed
 * }
 * ```
 *
 * It is similar to [use] (or Java try-with-resources) when handling [AutoCloseable].
 *
 * ```kotlin
 * object.getAutoCloseableObject().use { obj ->
 *   // obj.close() automatically closed at the end of `use` block scope.
 * }
 * ```
 *
 * @param initialValue initial value to set to underlying backing field
 * @param onUpdate callback called when the value is updated. The `oldValue` will be closed *after*
 *                 this callback is called and *after* the new value is set to underlying backing field.
 *                 This allows working safely with `oldValue` before it's closed.
 *
 * @see https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html
 * @see https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/use.html
 * @see https://kotlinlang.org/docs/delegated-properties.html
 */
class AutoCloseableDelegate<T>(initialValue: T?, private val onUpdate: ((oldValue: T?) -> Unit)? = null)
    : ReadWriteProperty<Any, T?>
        where T : AutoCloseable? {
    private var value: T? = initialValue

    override fun getValue(thisRef: Any, property: KProperty<*>): T? {
        return value
    }

    @Suppress("ConvertTryFinallyToUseCall")
    override fun setValue(thisRef: Any, property: KProperty<*>, value: T?) {
        val oldValue = this.value
        // overwriting with same value is no-op
        if (oldValue === value)
            return
        try {
            this.value = value
            onUpdate?.invoke(oldValue)
        } finally {
            oldValue?.close()
        }
    }
}

/**
 * Convenience helper for [AutoCloseableDelegate].
 *
 * @see [AutoCloseableDelegate]
 */
fun <T> autoCloseable(initialValue: T? = null, onUpdate: ((oldValue: T?) -> Unit)? = null): AutoCloseableDelegate<T>
        where T : AutoCloseable? {
    return AutoCloseableDelegate(initialValue, onUpdate)
}
