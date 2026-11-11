package com.thkim.jijang.util

import android.database.sqlite.SQLiteException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * 파일 입출력·엑셀/이미지 해석처럼 "실패할 수 있다고 예상되는" 작업을 실행한다.
 * 예상한 종류(입출력, 잘못된 값/상태)의 실패만 돌려주고, 코루틴 취소는 그대로 다시 던진다.
 * 그 밖의 예상 못한 오류는 삼키지 않고 그대로 올라가 오류 화면에서 확인된다.
 *
 * @return 실패 원인, 성공하면 null
 */
inline fun tryExpected(block: () -> Unit): Exception? {
    try {
        block()
        return null
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        return e
    } catch (e: IllegalArgumentException) {
        return e
    } catch (e: SQLiteException) {
        return e
    } catch (e: IllegalStateException) {
        return e
    }
}
