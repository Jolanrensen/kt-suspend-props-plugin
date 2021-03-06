import kotlinx.coroutines.delay
import kotlin.reflect.*

// TODO remove when this is no longer needed in the future
annotation class SuspendProp

//suspend fun main() {
//    testProp++
//}
//
private suspend var testProp: Int
    get() {
        delay(5)
        return 3
    }
    set(value) {
        delay(5)
        println(value)
    }
//
//private var test: Int
//    get() = 5
//    suspend set(value) {
//        delay(23)
//        println(value)
//    }


class Test {
    suspend operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
        delay(5)
        return 5
    }
    suspend operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        delay(5)
        println(value)
    }
}

suspend var a: Int by Test()

suspend fun main() {
    testProp++
    a += 5
}


//class Test {
//    @SuspendProp
//    operator fun getValue(thisRef: Any?, property: KProperty<*>): Int =
//        throw IllegalStateException()
//
//    suspend fun _suspendProp_getValue(thisRef: Any?, property: KProperty<*>): Int {
//        delay(2000)
//        return 5
//    }
//
//    @SuspendProp
//    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int): Unit =
//        throw IllegalStateException()
//
//    suspend fun _suspendProp_setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
//        println(value)
//    }
//}
//
//var _suspendProp_c = Test()
//
//@SuspendProp
//var c by _suspendProp_c
//
//
//suspend fun b() {
//    _suspendProp_c._suspendProp_setValue(null, ::c, 5)
}