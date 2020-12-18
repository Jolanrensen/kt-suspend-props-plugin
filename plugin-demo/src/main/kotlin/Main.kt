import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext


suspend fun main() {
    testProp++
}


private suspend var testProp: Int
    get() {
        // say what
        return withContext(Dispatchers.IO) {
            delay(2000)
            3
        }
    }
    set(value) = println(value)