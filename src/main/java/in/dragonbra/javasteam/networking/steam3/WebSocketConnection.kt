package `in`.dragonbra.javasteam.networking.steam3

import `in`.dragonbra.javasteam.util.log.LogManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.coroutines.CoroutineContext
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class WebSocketConnection :
    Connection(),
    CoroutineScope {

    companion object {
        private val logger = LogManager.getLogger<WebSocketConnection>()
    }

    private val job: Job = SupervisorJob()

    private var client: HttpClient? = null

    private var session: WebSocketSession? = null

    private var endpoint: InetSocketAddress? = null

    private var lastFrameTime = System.currentTimeMillis()

    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    override fun connect(endPoint: InetSocketAddress, timeout: Int) {
        launch {
            logger.debug("Trying connection to ${endPoint.hostName}:${endPoint.port}")

            try {
                endpoint = endPoint

                client = HttpClient(CIO) {
                    install(WebSockets) {
                        pingInterval = timeout.toDuration(DurationUnit.SECONDS)
                    }
                }

                val session = client?.webSocketSession {
                    url {
                        host = endPoint.hostName
                        port = endPoint.port
                        protocol = URLProtocol.WSS
                        path("cmsocket/")
                    }
                }

                this@WebSocketConnection.session = session

                startConnectionMonitoring()

                launch {
                    try {
                        session?.incoming?.consumeEach { frame ->
                            when (frame) {
                                is Frame.Binary -> {
                                    // logger.debug("on Binary ${frame.data.size}")
                                    lastFrameTime = System.currentTimeMillis()
                                    onNetMsgReceived(NetMsgEventArgs(frame.readBytes(), currentEndPoint))
                                }

                                is Frame.Close -> disconnect(false)

                                is Frame.Ping -> logger.debug("Received pong")

                                // Never Used.
                                is Frame.Pong -> logger.debug("Received pong")

                                // Never Used.
                                is Frame.Text -> logger.debug("Received plain text ${frame.readText()}")
                            }
                        }
                    } catch (e: CancellationException) {
                        // This won't print most times.
                        logger.debug("Websocket cancelling: ${e.message}")
                    } catch (e: Exception) {
                        logger.error("An error occurred while receiving data", e)
                        disconnect(false)
                    }
                }

                logger.debug("Connected to ${endPoint.hostName}:${endPoint.port}")
                onConnected()
            } catch (e: Exception) {
                logger.error("An error occurred setting up the web socket client", e)
                disconnect(false)
            }
        }
    }

    override fun disconnect(userInitiated: Boolean) {
        logger.debug("Disconnect called: $userInitiated")
        launch {
            try {
                session?.close()
                client?.close()
            } finally {
                session = null
                client = null

                job.cancelChildren()
            }

            onDisconnected(userInitiated)
        }
    }

    override fun send(data: ByteArray) {
        launch {
            try {
                val frame = Frame.Binary(true, data)
                session?.send(frame)
            } catch (e: Exception) {
                logger.error("An error occurred while sending data", e)
                disconnect(false)
            }
        }
    }

    override fun getLocalIP(): InetAddress = InetAddress.getLocalHost()

    override fun getCurrentEndPoint(): InetSocketAddress? = endpoint

    override fun getProtocolTypes(): ProtocolTypes = ProtocolTypes.WEB_SOCKET

    /**
     * Rudimentary watchdog
     *
     * v1.8.0-wepwawet13 (WallpaperSuite bug check): the threshold was 30
     * seconds of NO INCOMING BINARY FRAMES. That was too aggressive:
     *
     *  1. `lastFrameTime` is only refreshed by incoming Binary CM frames.
     *     WebSocket-level PING/PONG frames (handled inside ktor when
     *     `pingInterval` is configured) do NOT refresh it, so transport
     *     liveness and this watchdog measure different things.
     *  2. The CMClient heartbeat interval is SERVER-CONTROLLED
     *     (`legacyOutOfGameHeartbeatSeconds` from the logon response).
     *     If Steam ever specifies an interval ≥ 30s, this watchdog would
     *     disconnect perfectly healthy connections between heartbeats.
     *  3. During depot downloads all bulk traffic flows over SEPARATE
     *     steamcontent.com CDN connections; the CM socket can legitimately
     *     go quiet for extended stretches while the download is healthy.
     *
     * Transport-level death is already detected by (a) the
     * `client/session.isActive` checks above and (b) ktor's pingInterval
     * ping/pong failure. This watchdog is the last-resort app-layer check,
     * so it now fires at 5 MINUTES of total CM silence instead of 30s —
     * still fast enough to give the user a clear error instead of a hang,
     * but never fast enough to abort a healthy download.
     */
    private fun startConnectionMonitoring() {
        launch {
            while (isActive) {
                if (client?.isActive == false || session?.isActive == false) {
                    logger.error("Client or Session is no longer active")
                    disconnect(userInitiated = false)
                }

                val timeSinceLastFrame = System.currentTimeMillis() - lastFrameTime

                // logger.debug("Watchdog status: $timeSinceLastFrame")
                when {
                    timeSinceLastFrame > 300000 -> {
                        logger.error("Watchdog: No response for 5 minutes. Disconnecting from steam")
                        disconnect(userInitiated = false)
                        break
                    }

                    timeSinceLastFrame > 250000 -> logger.debug("Watchdog: No response for 250 seconds")

                    timeSinceLastFrame > 200000 -> logger.debug("Watchdog: No response for 200 seconds")

                    timeSinceLastFrame > 150000 -> logger.debug("Watchdog: No response for 150 seconds")
                }

                delay(5000)
            }
        }
    }
}
