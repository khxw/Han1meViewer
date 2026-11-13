package com.yenaly.han1meviewer.logic.network

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.channels.SocketChannel
import javax.net.SocketFactory

/**
 * 把 TLS ClientHello 的第一次写入拆成两个 TCP 分片。
 *
 * 目的：像 GFW 这种只解析单个数据包（或不做 TCP 重组）的 DPI，拿不到完整的 SNI，
 * 就不会对连接发起 RST。拆出来的第一片只有 1 字节（TLS 记录头都没凑齐），
 * 后面再补上剩余部分。
 *
 * 只在漫画的 OkHttpClient 上使用（socketFactory），主站/视频完全不受影响。
 * 如果目标网络的 DPI 会重组 TCP 分片，这个办法就不起作用——只能实测。
 */
class SplittingSocketFactory(
    /** 第一片的字节数，1 表示只发 1 个字节就断开本段。 */
    private val splitSize: Int = 1,
    /** 两片之间的等待毫秒数，确保它们落在不同的 TCP 段里。 */
    private val splitDelayMillis: Long = 20,
) : SocketFactory() {

    override fun createSocket(): Socket = SplittingSocket()

    override fun createSocket(host: String?, port: Int): Socket =
        SplittingSocket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int,
    ): Socket = SplittingSocket().apply {
        bind(InetSocketAddress(localHost, localPort))
        connect(InetSocketAddress(host, port))
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket =
        SplittingSocket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int,
    ): Socket = SplittingSocket().apply {
        bind(InetSocketAddress(localAddress, localPort))
        connect(InetSocketAddress(address, port))
    }

    private inner class SplittingSocket : Socket() {

        private var delegate: Socket? = null
        private var splittingOutput: OutputStream? = null

        private fun impl(): Socket = delegate ?: Socket().also { delegate = it }

        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            impl().connect(endpoint, timeout)
            // 连接成功后立刻关掉 Nagle，保证两片不会被内核合并
            runCatching { impl().tcpNoDelay = true }
        }

        override fun connect(endpoint: SocketAddress?) {
            impl().connect(endpoint)
            runCatching { impl().tcpNoDelay = true }
        }

        override fun getOutputStream(): OutputStream {
            splittingOutput?.let { return it }
            val created = SplittingOutputStream(impl().getOutputStream(), splitSize, splitDelayMillis)
            splittingOutput = created
            return created
        }

        override fun getInputStream(): InputStream = impl().getInputStream()

        override fun close() {
            runCatching { delegate?.close() }
        }

        override fun isConnected(): Boolean = delegate?.isConnected ?: false

        override fun isBound(): Boolean = delegate?.isBound ?: false

        override fun isClosed(): Boolean = delegate?.isClosed ?: true

        override fun isInputShutdown(): Boolean = delegate?.isInputShutdown ?: true

        override fun isOutputShutdown(): Boolean = delegate?.isOutputShutdown ?: true

        override fun shutdownInput() = impl().shutdownInput()

        override fun shutdownOutput() = impl().shutdownOutput()

        override fun getInetAddress(): InetAddress? = delegate?.inetAddress

        override fun getLocalAddress(): InetAddress =
            delegate?.localAddress ?: InetAddress.getLoopbackAddress()

        override fun getPort(): Int = delegate?.port ?: 0

        override fun getLocalPort(): Int = delegate?.localPort ?: -1

        override fun getRemoteSocketAddress(): SocketAddress? = delegate?.remoteSocketAddress

        override fun getLocalSocketAddress(): SocketAddress? = delegate?.localSocketAddress

        override fun setTcpNoDelay(on: Boolean) {
            if (delegate?.isConnected == true) runCatching { impl().tcpNoDelay = on }
        }

        override fun getTcpNoDelay(): Boolean = runCatching { impl().tcpNoDelay }.getOrDefault(true)

        override fun setSoTimeout(timeout: Int) {
            if (delegate?.isConnected == true) runCatching { impl().soTimeout = timeout }
        }

        override fun getSoTimeout(): Int = runCatching { impl().soTimeout }.getOrDefault(0)

        override fun setKeepAlive(on: Boolean) {
            if (delegate?.isConnected == true) runCatching { impl().keepAlive = on }
        }

        override fun getKeepAlive(): Boolean = runCatching { impl().keepAlive }.getOrDefault(false)

        override fun setSoLinger(on: Boolean, linger: Int) {
            if (delegate?.isConnected == true) runCatching { impl().setSoLinger(on, linger) }
        }

        override fun getSoLinger(): Int = runCatching { impl().soLinger }.getOrDefault(-1)

        override fun setReuseAddress(on: Boolean) {
            runCatching { impl().reuseAddress = on }
        }

        override fun getReuseAddress(): Boolean =
            runCatching { impl().reuseAddress }.getOrDefault(false)

        override fun setSendBufferSize(size: Int) { runCatching { impl().sendBufferSize = size } }

        override fun getSendBufferSize(): Int = runCatching { impl().sendBufferSize }.getOrDefault(0)

        override fun setReceiveBufferSize(size: Int) { runCatching { impl().receiveBufferSize = size } }

        override fun getReceiveBufferSize(): Int =
            runCatching { impl().receiveBufferSize }.getOrDefault(0)

        override fun setTrafficClass(tc: Int) { runCatching { impl().trafficClass = tc } }

        override fun getTrafficClass(): Int = runCatching { impl().trafficClass }.getOrDefault(0)

        override fun setPerformancePreferences(
            connectionTime: Int,
            latency: Int,
            bandwidth: Int,
        ) = impl().setPerformancePreferences(connectionTime, latency, bandwidth)

        override fun getChannel(): SocketChannel? = null
    }

    /**
     * 只改第一次写入：拆成 [splitSize] 字节 + 剩余部分，中间留一小段时间。
     * 后续写入原样透传，不影响正常的 TLS 记录分片。
     */
    private class SplittingOutputStream(
        private val delegate: OutputStream,
        private val splitSize: Int,
        private val splitDelayMillis: Long,
    ) : OutputStream() {

        private var alreadySplit = false

        override fun write(b: Int) {
            delegate.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (alreadySplit || len <= splitSize) {
                delegate.write(b, off, len)
                return
            }
            alreadySplit = true
            delegate.write(b, off, splitSize)
            delegate.flush()
            if (splitDelayMillis > 0) {
                runCatching { Thread.sleep(splitDelayMillis) }
            }
            delegate.write(b, off + splitSize, len - splitSize)
            delegate.flush()
        }

        override fun flush() {
            delegate.flush()
        }

        override fun close() {
            delegate.close()
        }
    }
}
