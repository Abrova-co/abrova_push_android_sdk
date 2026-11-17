package ir.abrova.push.sdk.internal

import org.eclipse.paho.client.mqttv3.MqttException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLHandshakeException

/** One word from a fixed list, from the error's type and numeric codes only: never its message. */
class SdkErrorReasonTest {
    @Test fun `network errors by type`() {
        assertEquals("timeout", SdkErrorReason.of(SocketTimeoutException("Read timed out")))
        assertEquals("dns", SdkErrorReason.of(UnknownHostException("push-api.abrova.ir")))
        assertEquals("refused", SdkErrorReason.of(ConnectException("Connection refused")))
        assertEquals("noroute", SdkErrorReason.of(NoRouteToHostException()))
        assertEquals("eof", SdkErrorReason.of(EOFException()))
        assertEquals("socket", SdkErrorReason.of(SocketException("Software caused connection abort")))
        assertEquals("other", SdkErrorReason.of(IOException("something")))
        assertEquals("other", SdkErrorReason.of(null))
    }

    @Test fun `the most telling word in the cause chain wins`() {
        // A handshake that failed on the certificate says cert, not tls_handshake.
        val cert = SSLHandshakeException("handshake").apply { initCause(CertPathValidatorException("not trusted")) }
        assertEquals("cert", SdkErrorReason.of(cert))
        assertEquals("tls_handshake", SdkErrorReason.of(SSLHandshakeException("handshake")))
        // Wrapped: the cause decides.
        assertEquals("timeout", SdkErrorReason.of(RuntimeException("wrap", IOException("io", SocketTimeoutException()))))
    }

    @Test fun `MQTT errors by their reason code, and code 0 falls back to the cause`() {
        assertEquals("mqtt32110", SdkErrorReason.of(MqttException(MqttException.REASON_CODE_CONNECT_IN_PROGRESS.toInt())))
        assertEquals("mqtt5", SdkErrorReason.of(MqttException(MqttException.REASON_CODE_NOT_AUTHORIZED.toInt())))
        // MqttException (0) = "client exception": use what is under it.
        assertEquals("timeout", SdkErrorReason.of(MqttException(SocketTimeoutException("Read timed out"))))
        assertEquals("other", SdkErrorReason.of(MqttException(0)))
    }

    @Test fun `an MQTT error that wraps a cause is described by the cause (seen on a phone - 32103 over ECONNREFUSED)`() {
        val unableToConnect = MqttException(MqttException.REASON_CODE_SERVER_CONNECT_ERROR.toInt(), ConnectException("failed to connect"))
        assertEquals("refused", SdkErrorReason.of(unableToConnect))
        val lost = MqttException(MqttException.REASON_CODE_CONNECTION_LOST.toInt(), EOFException())
        assertEquals("eof", SdkErrorReason.of(lost))
        // A cause that says nothing more: the MQTT code.
        assertEquals("mqtt32109", SdkErrorReason.of(MqttException(MqttException.REASON_CODE_CONNECTION_LOST.toInt(), RuntimeException("x"))))
    }

    @Test fun `programming errors and HTTP`() {
        assertEquals("state", SdkErrorReason.of(IllegalStateException("x")))
        assertEquals("arg", SdkErrorReason.of(IllegalArgumentException("ssl://bad host:8883")))
        assertEquals("parse", SdkErrorReason.of(com.google.gson.JsonSyntaxException("x")))
        assertEquals("http503", SdkErrorReason.http(503))
        assertEquals("other", SdkErrorReason.http(42))
    }

    @Test fun `the entry key carries port and network only when valid`() {
        assertEquals("timeout.8883.wifi", SdkErrorReason.key("timeout", 8883, "wifi"))
        assertEquals("timeout.8883", SdkErrorReason.key("timeout", 8883, "unknown"))
        assertEquals("timeout.cellular", SdkErrorReason.key("timeout", null, "cellular"))
        assertEquals("timeout", SdkErrorReason.key("timeout", 0, null))
    }

    @Test fun `no message, host or address ever reaches the reason`() {
        val words = listOf(
            SdkErrorReason.of(UnknownHostException("pn-tcp.abrova.ir")),
            SdkErrorReason.of(ConnectException("failed to connect to /10.0.0.1 (port 8883)")),
            SdkErrorReason.of(RuntimeException("user dana@acme.test")),
        )
        assertEquals(listOf("dns", "refused", "other"), words)
    }
}
