# Suspend Property Plugin

A Kotlin compiler plugin which adds suspend properties to the language.

### Warning: This is just a proof of concept for now!


## Usage
See [plugin-demo](./plugin-demo/src/main/kotlin/Main.kt) for full examples
```kotlin
import kotlinx.coroutines.delay

// NOTE: For now, you need to create an annotation class SuspendProp.
// This will not be needed in the future anymore once I figure out why additionalSources won't work
annotation class SuspendProp

suspend fun main() {
    testProp++
}

private suspend var testProp: Int
    get() {
        delay(2000)
        return 3
    }
    set(value) {
        delay(2000)
        println(value)
    }
```

or

```kotlin
import kotlinx.coroutines.delay

// NOTE: For now, you need to create an annotation class SuspendProp.
// This will not be needed in the future anymore once I figure out why additionalSources won't work
annotation class SuspendProp

fun main() {
    println(testProp)
    runBlocking { test() }
}

suspend fun test() {
    testProp = 5
}

private var testProp: Int
    get() = 3
    suspend set(value) {
        delay(2000)
        println(value)
    }
```

## Future improvements
1. Write Intellij plugin
2. Suspend Delegates (suspend operator fun get/setValue)
3. Interface support
4. Robust naming of under-the-hood-created functions
5. Making sure creating an annotation class SuspendProp by the user is no longer needed
3. Local Suspend Delegates (might require variable references to work)

## How does it work?
The plugin essentially rewrites the property into suspend functions. It works in two stages:

The first stage happens after the analysis phase of the compilation. It uses the Quote system of
Arrow Meta to rewrite the code you have written to something the "normal" Kotlin compiler understands.
This means that no more `suspend var` notations must exist as well no suspending calls in non-suspend 
blocks.

Calls to the suspend property must also be rerouted, but this is not yet possible in this stage, as 
this stage essentially looks at the file in a text-only format, not really understanding what's going
on in the code. We can however help the IR stage a bit with this by adding an annotation.

If we take the fist example, the code is rewritten by the plugin to this:
```kotlin
import kotlinx.coroutines.delay

annotation class SuspendProp

suspend fun main() {
    testProp++
}

@SuspendProp
private var testProp: Int
    get() = throw IllegalStateException("This call is replaced with _suspendProp_getTestProp() at compile time.")
    set(value) = throw IllegalStateException("This call is replaced with _suspendProp_setTestProp() at compile time.")
    
private suspend fun _suspendProp_getTestProp(): Int {
    delay(2000)
    return 3
}
private suspend fun _suspendProp_setTestProp(value: Int) {
    delay(2000)
    println(value)
}
```

As you can see, we keep a property without `suspend` to make sure it compiles (`::references` also still work).

Now we can use the IR to reroute all calls to `testProp` to `_suspendProp_getTestProp()` and `_suspendProp_setTestProp()`.
So we can find all calls that start with `"<get-"` or `"<set-"` (property access calls) and if the property they're calling
has the `@SuspendProp` annotation we reroute the calls.

This final IR is not very readable, so I'll write the end result as if it was written in Kotlin:
```kotlin
import kotlinx.coroutines.delay

annotation class SuspendProp

suspend fun main() {
    _suspendProp_setTestProp(_suspendProp_getTestProp()+1)
}

@SuspendProp
private var testProp: Int
    get() = throw IllegalStateException("This call is replaced with _suspendProp_getTestProp() at compile time.")
    set(value) = throw IllegalStateException("This call is replaced with _suspendProp_setTestProp() at compile time.")
    
private suspend fun _suspendProp_getTestProp(): Int {
    delay(2000)
    return 3
}
private suspend fun _suspendProp_setTestProp(value: Int) {
    delay(2000)
    println(value)
}
```


## References
1. [Arrow Meta](https://meta.arrow-kt.io/)
2. [Arrow Meta examples](https://github.com/arrow-kt/arrow-meta-examples/tree/master/hello-world)

