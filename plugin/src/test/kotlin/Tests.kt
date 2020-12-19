import arrow.meta.plugin.testing.CompilerTest
import arrow.meta.plugin.testing.Dependency
import arrow.meta.plugin.testing.assertThis
import nl.jolanrensen.suspendPropertyPlugin.SuspendPropertyPlugin
import org.intellij.lang.annotations.Language
import org.junit.Test

class Tests {

    @Test
    fun `main test`() {

        @Language("kotlin")
        val source = """
            annotation class SuspendProp

            suspend fun main() {
                println(pointless(testProp))
                testProp = 2
                testProp += 2
            }

            fun pointless(something: Any?) = something

            suspend var testProp: Int
                get() {
                    // say what
                    return 3
                }
                set(value) = println(value)
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            annotation class SuspendProp

            suspend fun main() {
                println(pointless(testProp))
                testProp = 2
                testProp += 2
            }

            fun pointless(something: Any?) = something

            @SuspendProp
            var testProp: Int
               get() = throw IllegalStateException("This call is replaced with _suspendProp_getTestProp() at compile time.")
               set(value) = throw IllegalStateException("This call is replaced with _suspendProp_setTestProp() at compile time.")

            suspend fun _suspendProp_getTestProp(): Int {
                // say what
                return 3
            }
            suspend fun _suspendProp_setTestProp(value: Int) = println(value)
        """.trimIndent()

        assertThis(CompilerTest(
            config = {
                listOf(
                    addMetaPlugins(SuspendPropertyPlugin())
                )
            },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }

    @Test
    fun `2 props test`() {

        @Language("kotlin")
        val source = """
            annotation class SuspendProp

            suspend fun main() {
                testProp += 2
                testProp2 += 3
            }

            suspend var testProp: Int
                get() {
                    return 3
                }
                set(value) = println(value)

            suspend var testProp2: Int
                get() {
                    return 6
                }
                set(value) = println(value)
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            annotation class SuspendProp

            suspend fun main() {
                testProp += 2
                testProp2 += 3
            }

            @SuspendProp
            var testProp: Int
               get() = throw IllegalStateException("This call is replaced with _suspendProp_getTestProp() at compile time.")
               set(value) = throw IllegalStateException("This call is replaced with _suspendProp_setTestProp() at compile time.")

            suspend fun _suspendProp_getTestProp(): Int {
                return 3
            }
            suspend fun _suspendProp_setTestProp(value: Int) = println(value)

            @SuspendProp
            var testProp2: Int
               get() = throw IllegalStateException("This call is replaced with _suspendProp_getTestProp2() at compile time.")
               set(value) = throw IllegalStateException("This call is replaced with _suspendProp_setTestProp2() at compile time.")

            suspend fun _suspendProp_getTestProp2(): Int {
                return 6
            }
            suspend fun _suspendProp_setTestProp2(value: Int) = println(value)
        """.trimIndent()

        assertThis(CompilerTest(
            config = {

                listOf(addMetaPlugins(SuspendPropertyPlugin()))
            },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }


    @Test
    fun `reference test`() {

        @Language("kotlin")
        val source = """
            annotation class SuspendProp

            suspend fun main() {
                println(pointless(testProp))
                testProp = 2
                testProp += 2
                val a = ::testProp
            }

            fun pointless(something: Any?) = something

            suspend var testProp: Int
                get() {
                    // say what
                    return 3
                }
                set(value) = println(value)
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            annotation class SuspendProp

            suspend fun main() {
                println(pointless(testProp))
                testProp = 2
                testProp += 2
                val a = ::testProp
            }

            fun pointless(something: Any?) = something

            @SuspendProp
            var testProp: Int
               get() = throw IllegalStateException("This call is replaced with _suspendProp_getTestProp() at compile time.")
               set(value) = throw IllegalStateException("This call is replaced with _suspendProp_setTestProp() at compile time.")

            suspend fun _suspendProp_getTestProp(): Int {
                // say what
                return 3
            }
            suspend fun _suspendProp_setTestProp(value: Int) = println(value)
        """.trimIndent()

        assertThis(CompilerTest(
            config = {

                listOf(addMetaPlugins(SuspendPropertyPlugin()))
            },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }

    @Test
    fun `simple test`() {

        @Language("kotlin")
        val source = """
            annotation class SuspendProp

            suspend var testProp: Int 
                get() {
                   // say what
                   return 3 
               }
               set(value) = println(value)
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            annotation class SuspendProp
            
            @SuspendProp
            var testProp: Int
               get() = throw IllegalStateException("This call is replaced with _suspendProp_getTestProp() at compile time.")
               set(value) = throw IllegalStateException("This call is replaced with _suspendProp_setTestProp() at compile time.")

            suspend fun _suspendProp_getTestProp(): Int {
               // say what
               return 3
            }
            suspend fun _suspendProp_setTestProp(value: Int) = println(value)
        """.trimIndent()

        assertThis(CompilerTest(
            config = {

                listOf(addMetaPlugins(SuspendPropertyPlugin()))
            },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }

    @Test
    fun `simple test with access`() {

        @Language("kotlin")
        val source = """
            annotation class SuspendProp

            suspend fun testFun2(): Int = testProp
            
            suspend fun testFun() {
                val a: Int
                a = testProp
            }
            
            suspend var testProp: Int
               get() {
                   // say what
                   return 3 
               }
               set(value) = println(value)
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            annotation class SuspendProp

            suspend fun testFun2(): Int = testProp
            
            suspend fun testFun() {
                val a: Int
                a = testProp
            }
            
            @SuspendProp
            var testProp: Int
               get() = throw IllegalStateException("This call is replaced with _suspendProp_getTestProp() at compile time.")
               set(value) = throw IllegalStateException("This call is replaced with _suspendProp_setTestProp() at compile time.")
            
            suspend fun _suspendProp_getTestProp(): Int {
               // say what
               return 3
            }
            suspend fun _suspendProp_setTestProp(value: Int) = println(value)
        """.trimIndent()

        // after this, in IR testProp is mapped to _suspendProp_getTestProp()

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }

    @Test
    fun `simple test with visibility`() {

        @Language("kotlin")
        val source = """
            annotation class SuspendProp

            suspend fun test() = testProp

            private suspend var testProp: Int
               get() = 4
               set(value) {
                   println(value)
               }
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            annotation class SuspendProp

            suspend fun test() = testProp

            @SuspendProp
            private var testProp: Int
               get() = throw IllegalStateException("This call is replaced with _suspendProp_getTestProp() at compile time.")
               set(value) = throw IllegalStateException("This call is replaced with _suspendProp_setTestProp() at compile time.")
            
            private suspend fun _suspendProp_getTestProp(): Int = 4
            private suspend fun _suspendProp_setTestProp(value: Int) {
               println(value)
            }
            
        """.trimIndent()

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }

    @Test
    fun `val test`() {

        @Language("kotlin")
        val source = """
            annotation class SuspendProp

            private suspend val testProp: Int
               get() = 4
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            annotation class SuspendProp

            @SuspendProp
            private val testProp: Int
               get() = throw IllegalStateException("This call is replaced with _suspendProp_getTestProp() at compile time.")
            
            private suspend fun _suspendProp_getTestProp(): Int = 4
            
        """.trimIndent()

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }

    @Test
    fun `calling suspend fun from getter`() {

        @Language("kotlin")
        val source = """
            annotation class SuspendProp

            suspend fun testFun(): Int = 4

            private suspend val testProp: Int
               get() = testFun()
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            annotation class SuspendProp

            suspend fun testFun(): Int = 4
            
            @SuspendProp
            private val testProp: Int
               get() = throw IllegalStateException("This call is replaced with _suspendProp_getTestProp() at compile time.")
            
            private suspend fun _suspendProp_getTestProp(): Int = testFun()
            
        """.trimIndent()

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }


    @Test // TODO this probably should fail
    fun `test with initializer`() {

        @Language("kotlin")
        val source = """
            annotation class SuspendProp

            suspend fun testFun(): Int = 4

            private suspend var testProp: Int = testFun()
               get() = field + 1
               set(value) {
                   println(value)
               }
        """.trimIndent()

        // TODO!!
        @Language("kotlin")
        val expectedOutput = """
            annotation class SuspendProp

            suspend fun testFun(): Int = 4
            
            @SuspendProp
            private var testProp: Int
               get() = throw IllegalStateException("This call is replaced with _suspendProp_getTestProp() at compile time.")
               set(value) = throw IllegalStateException("This call is replaced with _suspendProp_setTestProp() at compile time.")
            
            private var testProp: Int? = null
            private suspend fun _suspendProp_getTestProp(): Int = testProp + 1
            private suspend fun _suspendProp_setTestProp(value: Int) {
               println(value)
            }
            
        """.trimIndent()

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }

    @Test // TODO will fail for now
    fun `test with only initializer`() {

        @Language("kotlin")
        val source = """
            private suspend var testProp: Int = 4
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            annotation class _SuspendProp
            @_SuspendProp
            private var testProp: Int = 4
            
            private suspend fun _suspendProp_getTestProp(): Int = 4
            private suspend fun _suspendProp_setTestProp(value: Int) {
               println(value)
            }
            
        """.trimIndent()

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }

    @Test
    fun `Just one suspend`() {

        @Language("kotlin")
        val source = """
            annotation class SuspendProp

            suspend fun testFun() {
                val a = testProp
            }
            
            fun testFun2() {
                testProp = 7
            }

            private var testProp: Int
               suspend get() {
                   // say what
                   return 3 
               }
               set(value) = println(value)
        """.trimIndent()

        @Language("kotlin")
        val expectedOutput = """
            annotation class SuspendProp

            suspend fun testFun() {
                val a = testProp
            }

            fun testFun2() {
                testProp = 7
            }
            
            @SuspendProp
            private var testProp: Int
               get() = throw IllegalStateException("This call is replaced with _suspendProp_getTestProp() at compile time.")
               set(value) = throw IllegalStateException("This call is replaced with _suspendProp_setTestProp() at compile time.")
            
            private suspend fun _suspendProp_getTestProp(): Int {
               // say what
               return 3
            }
            private fun _suspendProp_setTestProp(value: Int) = println(value)
        """.trimIndent()

        // after this, in IR testProp is mapped to _suspendProp_getTestProp()

        assertThis(CompilerTest(
            config = { listOf(addMetaPlugins(SuspendPropertyPlugin())) },
            code = { source.source },
            assert = { quoteOutputMatches(expectedOutput.source) }
        ))
    }
}