import kotlinx.coroutines.delay

// TODO remove when this is no longer needed in the future
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