import arrow.meta.plugin.testing.CompilerTest
import arrow.meta.plugin.testing.assertThis
import nl.jolanrensen.suspendPropertyPlugin.SuspendPropertyPlugin
import org.intellij.lang.annotations.Language
import org.junit.Test

class DelegateTests {

    @Test
    fun `combination test`() {

        @Language("kotlin")
        val source = """
            import kotlin.reflect.KProperty

            annotation class SuspendProp

            suspend fun doesNothing() {}

            class Test {
                suspend operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = 5
                suspend operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                    doesNothing()
                    println(test)
                }
            }

            suspend var test: Int
                get() = 6
                set(value) { doesNothing() }

            suspend var c: Int by Test()
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            import kotlin.reflect.KProperty

            annotation class SuspendProp

            suspend fun doesNothing() {}

            class Test {
                @SuspendProp
                operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = throw IllegalStateException("This call is replaced with _suspendProp_getValue() at compile time.")

                suspend fun _suspendProp_getValue(thisRef: Any?, property: KProperty<*>): Int = 5

                @SuspendProp
                operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int): Unit = throw IllegalStateException("This call is replaced with _suspendProp_setValue() at compile time.")

                suspend fun _suspendProp_setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                    doesNothing()
                    println(test)
                }
            }

            @SuspendProp
            var test: Int
               get() = throw IllegalStateException("This call is replaced with _suspendProp_getTest() at compile time.")
               set(value) = throw IllegalStateException("This call is replaced with _suspendProp_setTest() at compile time.")
    
            suspend fun _suspendProp_getTest(): Int = 6
            suspend fun _suspendProp_setTest(value: Int) { doesNothing() }

            val _suspendProp_c = Test()

            @SuspendProp
            var c: Int
                get() = throw IllegalStateException("This call is replaced with _suspendProp_getC() at compile time.")
                set(value) = throw IllegalStateException("This call is replaced with _suspendProp_setC() at compile time.")

            suspend fun _suspendProp_getC(): Int = _suspendProp_c._suspendProp_getValue(null, ::c)
            suspend fun _suspendProp_setC(value: Int) = _suspendProp_c._suspendProp_setValue(null, ::c, value)
        """.trimIndent()

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))

    }

    @Test
    fun `simple test 1`() {

        @Language("kotlin")
        val source = """
            import kotlin.reflect.KProperty

            annotation class SuspendProp

            suspend fun doesNothing() {}

            class Test {
                suspend operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = 5
                suspend operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                    doesNothing()
                    println(value)
                }
                fun testFun() {}
            }

            suspend var c: Int by Test()

            suspend fun main() {
                c = 5
                val test = Test()
                test.testFun()
            }
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            import kotlin.reflect.KProperty

            annotation class SuspendProp

            suspend fun doesNothing() {}

            class Test {
                @SuspendProp 
                operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = throw IllegalStateException("This call is replaced with _suspendProp_getValue() at compile time.")

                suspend fun _suspendProp_getValue(thisRef: Any?, property: KProperty<*>): Int = 5

                @SuspendProp
                operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int): Unit = throw IllegalStateException("This call is replaced with _suspendProp_setValue() at compile time.")

                suspend fun _suspendProp_setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                    doesNothing()
                    println(value)
                }
                fun testFun() {}
            }

            val _suspendProp_c = Test()

            @SuspendProp
            var c: Int
                get() = throw IllegalStateException("This call is replaced with _suspendProp_getC() at compile time.")
                set(value) = throw IllegalStateException("This call is replaced with _suspendProp_setC() at compile time.")

            suspend fun _suspendProp_getC(): Int = _suspendProp_c._suspendProp_getValue(null, ::c)
            suspend fun _suspendProp_setC(value: Int) = _suspendProp_c._suspendProp_setValue(null, ::c, value)

            suspend fun main() {
                c = 5
                val test = Test()
                test.testFun()
            }
        """.trimIndent()

        // in IR replace all calls to @SuspendProp annotated elements

        // TODO [c = 5] becomes [_suspendProp_c._suspendProp_setValue(null, ::c, 5)] in IR

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }

    @Test
    fun `simple test 2`() {

        @Language("kotlin")
        val source = """
            import kotlin.reflect.KProperty

            annotation class SuspendProp

            suspend fun doesNothing() {}

            class Test {
                suspend operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = 5
                suspend operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                    doesNothing()
                    println(value)
                }
                fun testFun() {}
            }

            suspend var c: Int by Test()

            suspend fun main() {
                c = 5
                val test = Test()
                test.testFun()
                println(test.getValue(null, ::c))
            }
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            import kotlin.reflect.KProperty

            annotation class SuspendProp

            suspend fun doesNothing() {}

            class Test {
                @SuspendProp 
                operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = throw IllegalStateException("This call is replaced with _suspendProp_getValue() at compile time.")

                suspend fun _suspendProp_getValue(thisRef: Any?, property: KProperty<*>): Int = 5

                @SuspendProp
                operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int): Unit = throw IllegalStateException("This call is replaced with _suspendProp_setValue() at compile time.")

                suspend fun _suspendProp_setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                    doesNothing()
                    println(value)
                }
                fun testFun() {}
            }

            val _suspendProp_c = Test()

            @SuspendProp
            var c: Int
                get() = throw IllegalStateException("This call is replaced with _suspendProp_getC() at compile time.")
                set(value) = throw IllegalStateException("This call is replaced with _suspendProp_setC() at compile time.")

            suspend fun _suspendProp_getC(): Int = _suspendProp_c._suspendProp_getValue(null, ::c)
            suspend fun _suspendProp_setC(value: Int) = _suspendProp_c._suspendProp_setValue(null, ::c, value)

            suspend fun main() {
                c = 5
                val test = Test()
                test.testFun()
                println(test.getValue(null, ::c))
            }
        """.trimIndent()

        // in IR replace all calls to @SuspendProp annotated elements

        // TODO [c = 5] becomes [_suspendProp_c._suspendProp_setValue(null, ::c, 5)] in IR
        // TODO [test.getValue(null, ::c)] becomes [test._suspendProp_getValue(null, ::c)] in IR

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }

    // TODO this will not work for now due to lack of local references
    @Test
    fun `local delegate suspend prop`() {

        @Language("kotlin")
        val source = """
            import kotlin.reflect.KProperty

            annotation class SuspendProp

            class Test {
                suspend operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
                    return 5
                }
                suspend operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                    println(value)
                }
            }

            suspend fun b() {
                suspend var a: Int by Test()
                a = 5
            }

        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            import kotlin.reflect.KProperty

            annotation class SuspendProp

            class Test {
                @SuspendProp
                operator fun getValue(thisRef: Any?, property: KProperty<*>): Int =
                    throw IllegalStateException()

                suspend fun _suspendProp_getValue(thisRef: Any?, property: KProperty<*>): Int {
                    return 5
                }

                @SuspendProp
                operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int): Unit =
                    throw IllegalStateException()

                suspend fun _suspendProp_setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                    println(value)
                }
            }

            suspend fun b() {
                val _suspendProp_a = Test()
                var a by _suspendProp_a
                a = 5
            }

        """.trimIndent()

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }
}