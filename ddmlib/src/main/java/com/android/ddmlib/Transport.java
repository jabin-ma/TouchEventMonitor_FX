package com.android.ddmlib;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.android.ddmlib.AdbHelper.AdbResponse;

public class Transport implements Runnable {
	private SocketChannel adbChan;

	private IDevice device;

	private Selector selector;

	private ByteBuffer buffer = ByteBuffer.allocate(16384);

	private CountDownLatch configFinish = new CountDownLatch(1);

	private IShellOutputReceiver rciv;

	private ExecutorService executorService = Executors.newSingleThreadExecutor();

	public Transport(IDevice device, IShellOutputReceiver rciv) {
		super();
		this.device = device;
		this.rciv = rciv;
	}

	/**
	 * 进入shell环境
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 *             超时
	 */
	public boolean enter(int timeout, TimeUnit unit) throws IOException, InterruptedException {
		this.adbChan = SocketChannel.open();
		adbChan.configureBlocking(false);
		selector = Selector.open();
		adbChan.connect(AndroidDebugBridge.getSocketAddress());
		adbChan.register(selector, SelectionKey.OP_CONNECT);
		executorService.execute(this);
		return configFinish.await(timeout, unit);
	}

	/**
	 * 配置认证
	 * 
	 * @param channel
	 * @param dev
	 * @throws TimeoutException
	 * @throws AdbCommandRejectedException
	 * @throws IOException
	 */
	private void configureTransport(SocketChannel channel, IDevice dev)
			throws TimeoutException, AdbCommandRejectedException, IOException {
		AdbHelper.setDevice(adbChan, device);
		// start
		byte[] request = AdbHelper.formAdbRequest("shell:"); //$NON-NLS-1$
		AdbHelper.write(adbChan, request);
		AdbResponse resp = AdbHelper.readAdbResponse(adbChan, false /* readDiagString */);
		if (!resp.okay) {
			throw new AdbCommandRejectedException(resp.message);
		}
	}

	public static final String TAG = "Transport";

	private void closeKey(SelectionKey key) {
		Log.d(TAG, "CloseKey");
		if (key != null) {
			key.cancel();
			try {
				key.channel().close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		close();
	}

	public void close() {
		try {
			executorService.shutdownNow();
			if (adbChan != null) {
				adbChan.socket().close();
				adbChan.close();
				adbChan = null;
			}
			if (selector != null) {
				selector.close();
				selector = null;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void send(String str) {
		try {
			AdbHelper.write(adbChan, String.format("%s\r", str).getBytes());
		} catch (TimeoutException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		SelectionKey key = null;
		try {
			while (selector.select() > 0) {
				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> iterator = keys.iterator();
				while (iterator.hasNext()) {
					key = iterator.next();
					if (rciv == null || rciv.isCancelled()) {
						break;
					}
					SocketChannel socketchannel = (SocketChannel) key.channel();
					if (key.isConnectable()) {
						if (socketchannel.isConnectionPending()) {
							socketchannel.finishConnect();
							configureTransport(socketchannel, device);
						}
						configFinish.countDown();
						key.interestOps(SelectionKey.OP_READ);
					} else if (key.isReadable()) {
						buffer.clear();
						try {
							int count = socketchannel.read(buffer);
							if (count == -1) {
								closeKey(key);
								break;
							}
							rciv.addOutput(buffer.array(), buffer.arrayOffset(), buffer.position());
						} catch (Exception e) {
						}
						rciv.flush();
						buffer.rewind();
					}
					iterator.remove();
				}
			}
			Log.d(TAG, device + " transport end...");
		} catch (Exception e) {
			e.printStackTrace();
			closeKey(key);
		}
	}

}
