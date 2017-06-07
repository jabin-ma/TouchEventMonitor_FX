package com.android.ddmlib.monkey;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AdbHelper;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;

public class NetworkMonkey implements Callable<Void> {

	public static final int port = 1080;

	public static final String TAG = NetworkMonkey.class.getSimpleName();

	private static final HashMap<String, NetworkMonkey> monkeys = new HashMap<>();

	public static NetworkMonkey create(IDevice dev) throws IOException {
		String id = dev.getSerialNumber();
		if (monkeys.get(id) == null) {
			monkeys.put(id, new NetworkMonkey(dev));
		}
		return monkeys.get(id);
	}

	private SocketChannel adbChan;

	private Selector selector;

	private ByteBuffer buffer = ByteBuffer.allocate(3);

	private ExecutorService executorService = Executors.newSingleThreadExecutor();

	private IDevice mDev;

	public NetworkMonkey(IDevice dev) throws IOException {
		mDev = dev;
		autoReconnect();
	}

	private void autoReconnect() throws IOException {
		if (connect()) {
			Log.d(TAG, "连接成功");
		} else {
			try {
				mDev.createForward(1080, 1080);
				mDev.executeShellCommand("monkey --port 1080", null, 1, TimeUnit.SECONDS);
			} catch (TimeoutException e) {
				e.printStackTrace();
			} catch (AdbCommandRejectedException e) {
				e.printStackTrace();
			} catch (ShellCommandUnresponsiveException e) {
				autoReconnect();
			}
		}
	}

	private boolean connect() throws IOException {
		this.adbChan = SocketChannel.open();
		adbChan.configureBlocking(false);
		selector = Selector.open();
		adbChan.connect(new InetSocketAddress("127.0.0.1", port));
		adbChan.register(selector, SelectionKey.OP_CONNECT);
		try {
			executorService.submit(this).get(5, TimeUnit.SECONDS);
			return false;
		} catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
			return true;
		}
	}

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
			Log.d(TAG, "send..." + str);
			AdbHelper.write(adbChan, String.format("%s\n", str).getBytes());
		} catch (TimeoutException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private CountDownLatch mEventStep;

	public void send(String... args) {
		if (mEventStep != null) {
			try {
				mEventStep.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		mEventStep = new CountDownLatch(args.length - 1);
		for (String line : args)
			send(line);
		try {
			mEventStep.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void socketClosed() {
		Log.d(TAG, "socketClosed");
	}

	private void addOutput(byte[] array, int arrayOffset, int position) {
		mEventStep.countDown();
	}

	private void socketFinishConnect() {
	}

	@Override
	public Void call() throws Exception {
		SelectionKey key = null;
		try {
			while (selector != null && selector.select() > 0) {
				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> iterator = keys.iterator();
				while (iterator.hasNext()) {
					key = iterator.next();
					SocketChannel socketchannel = (SocketChannel) key.channel();
					if (key.isConnectable()) {
						if (socketchannel.isConnectionPending()) {
							socketchannel.finishConnect();
							socketFinishConnect();
						}
						key.interestOps(SelectionKey.OP_READ);
					} else if (key.isReadable()) {
						buffer.clear();
						try {
							int count = socketchannel.read(buffer);
							if (count == -1) {
								closeKey(key);
								break;
							}
							addOutput(buffer.array(), buffer.arrayOffset(), buffer.position());
						} catch (Exception e) {
						}
						buffer.rewind();
					} else if (key.isWritable()) {
						Log.d(TAG, "writable");
					}
					iterator.remove();
				}
			}
			socketClosed();
		} catch (Exception e) {
			closeKey(key);
		}
		return null;
	}
}
