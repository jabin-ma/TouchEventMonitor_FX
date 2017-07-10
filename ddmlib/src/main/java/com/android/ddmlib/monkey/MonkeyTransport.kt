package com.android.ddmlib.monkey

import com.android.ddmlib.AdbHelper
import com.android.ddmlib.IDevice
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.ddmlib.controller.KeyCode
import com.android.ddmlib.controller.SimpleRemoteController
import com.android.ddmlib.utils.d
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.*

class MonkeyTransport(var port: Int = 1080, var androidDevice: IDevice) : SimpleRemoteController() {
    override fun quit() {
        connect!!.writeSync("quit")
    }

    override fun done() {
        connect!!.writeSync("done")
    }

    override fun create() {
        if (connect != null && connect!!.state == State.STOP) {
            d("connect is arealdy connected")
            return;
        }
        d("next step...create connect")
        connect = Connect(port);
        connect?.autoCreateSocket(retry@ {
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

    override fun touchDown(x: Int, y: Int) {
        if (x < 0 || y < 0) d("touchDown!!!!!!")
        connect!!.writeSync("touch down $x $y")
    }

    override fun touchMove(x: Int, y: Int) {
        if (x < 0 || y < 0) d("touchMove!!!!!!")

        connect!!.writeSync("touch move $x $y")
    }

    override fun touchUp(x: Int, y: Int) {
        if (x < 0 || y < 0) d("touchUp!!!!!!")
        connect!!.writeSync("touch up $x $y", true)
    }

    override fun sleep(ms: Long) {
        connect!!.writeSync("sleep ${ms}")
    }

    override fun keyDown(key: KeyCode) {
        connect!!.writeSync("key down ${key.code}")
    }

    override fun keyUp(key: KeyCode) {
        connect!!.writeSync("key up ${key.code}", true)
    }

    private var connect: Connect? = null;


    /**
     * 返回值
     */
    internal enum class Response {
        OK, FAIL
    }

    /**
     * connect状态
     */
    enum class State {
        RUN, STOP, CONNECTED, DISCONNECTED
    }

    internal inner class Connect(val port: Int = 1080) : Callable<Void> {

        private var socketChannel: SocketChannel? = null

        private var selector: Selector? = null

        private val executorService = Executors.newSingleThreadExecutor()

        private val buffer = ByteBuffer.allocate(3)

        var state = State.STOP

        var mEventBuffer = StringBuilder()
        var mRetry: (() -> Boolean)? = null

        @Throws(IOException::class)
        fun autoCreateSocket(retry: () -> Boolean) {
            d("autoCreateSocket")
            mRetry = retry
            if (createSocket(2)) {
                //                Log.d(TAG, "连接成功");
                d("createSocket ok")
            } else {
                if (mRetry!!.invoke()) {
                    d("retry true")
                    autoCreateSocket(mRetry!!);
                } else {

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
        fun writeSync(text: String, flush: Boolean = false): Response {
            d("write $text")
            if (socketChannel == null) {
                autoCreateSocket(mRetry!!)
            }
            mEventBuffer.append(String.format("%s\n", text))
            //
            if (flush && !mEventBuffer.isEmpty()) {
                AdbHelper.write(socketChannel, mEventBuffer.toString().toByteArray())
                var line = mEventBuffer.count { it == '\n' }
                d("write ${mEventBuffer.toString()}-----> waiting response count $line")
                mEventBuffer.delete(0, mEventBuffer.length)
                for (i in 0..line - 1) {
                    d("handle response $i")
                    responseQueue.take();
                }
                d("finish return")
                return writeSync@ Response.OK
            }
            return Response.FAIL
        }




        @Throws(Exception::class)
        override fun call(): Void? {
            var key: SelectionKey? = null
            try {
                state = State.RUN
                d("while begin")
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
                                state = State.CONNECTED
                                d("state --> $state")
                            }
                            key.interestOps(SelectionKey.OP_READ)
                        } else if (key.isReadable) {
                            buffer.clear()
                            try {
                                val count = socketchannel.read(buffer)
                                if (count == -1) {
                                    closeKey(key);
                                    //关闭通道
                                    d("closekey")
                                    break
                                }
                                d("response :" + String(buffer.array(), buffer.arrayOffset(), buffer.position()))
                                //                                addOutput(buffer.array(), buffer.arrayOffset(), buffer.position());
                                responseQueue.add(Response.OK)
                                //接受到一次返回
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            buffer.rewind()
                        } else if (key.isWritable) {
                            //                            Log.d(TAG, "writable");
                        }
                        iterator.remove()
                    }
                }
                closeKey(null)
            } catch (e: Exception) {
                closeKey(key)
            } finally {
                state = State.STOP
            }
            d("while end")
            return null
        }

        private fun closeKey(key: SelectionKey?) {
            if (key != null) {
                key.cancel()
                try {
                    key.channel().close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }
            try {
                if (socketChannel != null) {
                    socketChannel!!.socket().close()
                    socketChannel!!.close()
                    socketChannel = null
                }
                if (selector != null) {
                    selector!!.close()
                    selector = null
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            state = State.STOP
        }
    }
}
