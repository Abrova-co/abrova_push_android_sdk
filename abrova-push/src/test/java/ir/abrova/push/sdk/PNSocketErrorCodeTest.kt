package ir.abrova.push.sdk

import ir.abrova.protocol.PNError
import ir.abrova.protocol.PNState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The public error codes apps receive. Only one changed in 0.1.23: a connection that failed while being set up
 * (the protocol says UNKNOWN while CONNECTING) is 1007 CONNECTION_SETUP_FAILED instead of 9999.
 */
class PNSocketErrorCodeTest {
    @Test fun `setup failure while connecting is 1007`() {
        assertEquals(AbrovaPushErrorCode.CONNECTION_SETUP_FAILED, PNSocketManager.errorCodeFor(PNError.Code.UNKNOWN, PNState.CONNECTING))
        assertEquals(1007, AbrovaPushErrorCode.CONNECTION_SETUP_FAILED.code)
    }

    @Test fun `every other mapping is unchanged`() {
        assertEquals(AbrovaPushErrorCode.UNKNOWN_ERROR, PNSocketManager.errorCodeFor(PNError.Code.UNKNOWN, PNState.CONNECTED))
        assertEquals(AbrovaPushErrorCode.UNKNOWN_ERROR, PNSocketManager.errorCodeFor(PNError.Code.UNKNOWN, null))
        assertEquals(AbrovaPushErrorCode.CONNECTION_FAILED, PNSocketManager.errorCodeFor(PNError.Code.CONNECTION_FAILED, PNState.CONNECTING))
        assertEquals(AbrovaPushErrorCode.CONNECTION_LOST, PNSocketManager.errorCodeFor(PNError.Code.CONNECTION_LOST, PNState.CONNECTED))
        assertEquals(AbrovaPushErrorCode.AUTHENTICATION_FAILED, PNSocketManager.errorCodeFor(PNError.Code.AUTH_FAILED, PNState.CONNECTING))
        assertEquals(AbrovaPushErrorCode.UNKNOWN_ERROR, PNSocketManager.errorCodeFor(PNError.Code.STREAM_FAILED, PNState.CONNECTED))
    }
}
