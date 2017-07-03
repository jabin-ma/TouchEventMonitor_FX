package com.android.ddmlib

import com.android.ddmlib.utils.d
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.*

class MonkeyTransport(var port: Int = 1080, var androidDevice: IDevice) {

    private var connect: Connect? = null;

    fun createConnect() {
        connect = Connect(port);
        connect?.autoCreateSocket(retry@{
            try {
                androidDevice.createForward(port, port)
                androidDevice.executeShellCommand(null, 1, TimeUnit.SECONDS, "monkey --port 1080")
            } catch (e: ShellCommandUnresponsiveException) {
                d("ShellCommandUnresponsiveException--true")
               return@retry true
            }
            d("retryfun return false")
            return@retry false
        })
    }


    internal enum class Response {
        OK, FAIL
    }

    internal inner class Connect(val port: Int = 1080) : Callable<Void> {

        private var socketChannel: SocketChannel? = null

        private var selector: Selector? = null

        private val executorService = Executors.newSingleThreadExecutor()

        private val buffer = ByteBuffer.allocate(3)

        @Throws(IOException::class)
        fun autoCreateSocket(retry: () -> Boolean) {
            d("autoCreateSocket")
            if (createSocket(2)) {
                //                Log.d(TAG, "连接成功");
                d("连接成功")
            } else {
                if (retry.invoke()) {
                    d("retry true")
                    autoCreateSocket(retry);
                }else{

                }
            }
        }


        @Throws(IOException::class)
        private fun createSocket(timeout: Int): Boolean {
            socketChannel = SocketChannel.open()
            socketChannel!!.configureBlocking(false)
            selector = Selector.open()
            socketChannel!!.connect(InetSocketAddress("127.0.0.1", port))
            socketChannel!!.register(selector, SelectionKey.OP_CONNECT)
            try {
                executorService.submit(this).get(timeout.toLong(), TimeUnit.SECONDS)
                return false
            } catch (e: InterruptedException) {
                return true
            } catch (e: ExecutionException) {
                return true
            } catch (e: java.util.concurrent.TimeoutException) {
                return true
            }

        }


        private val responseQueue = LinkedBlockingQueue<Response>()


        @Throws(InterruptedException::class)
        fun writeSync(text: String): Response {
            AdbHelper.write(socketChannel, String.format("%s\n", text).toByteArray())
            return responseQueue.take()
        }


        @Throws(Exception::class)
        override fun call(): Void? {
            var key: SelectionKey? = null
            try {
                while (selector != null && selector!!.select() > 0) {
                    val keys = selector!!.selectedKeys()
                    val iterator = keys.iterator()
                    while (iterator.hasNext()) {
                        key = iterator.next()
                        val socketchannel = key!!.channel() as SocketChannel
                        if (key.isConnectable) {
                            if (socketchannel.isConnectionPending) {
                                socketchannel.finishConnect()
                                //连接成功
                            }
                            key.interestOps(SelectionKey.OP_READ)
                        } else if (key.isReadable) {
                            buffer.clear()
                            try {
                                val count = socketchannel.read(buffer)
                                if (count == -1) {
                                    //                                    closeKey(key);
                                    //关闭通道
                                    break
                                }
                                //                                addOutput(buffer.array(), buffer.arrayOffset(), buffer.position());
                                responseQueue.add(Response.OK)
                                //接受到一次返回
                            } catch (e: Exception) {

                            }

                            buffer.rewind()
                        } else if (key.isWritable) {
                            //                            Log.d(TAG, "writable");
                        }

                        iterator.remove()
                    }
                }
                //                socketClosed();
            } catch (e: Exception) {
                //                closeKey(key);
            }

            return null
        }
    }
}
