/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ddmlib.handler;

/**
 * Class used to run a message loop for a thread. Threads by default do not have
 * a message loop associated with them; to create one, call {@link #prepare} in
 * the thread that is to run the loop, and then {@link #loop} to have it process
 * messages until the loop is stopped.
 *
 * <p>
 * Most interaction with a message loop is through the {@link Handler} class.
 *
 * <p>
 * This is a typical example of the implementation of a Looper thread, using the
 * separation of {@link #prepare} and {@link #loop} to create an initial Handler
 * to communicate with the Looper.
 *
 * <pre>
 * class LooperThread extends Thread {
 * 	public Handler mHandler;
 *
 * 	public void run() {
 * 		Looper.prepare();
 *
 * 		mHandler = new Handler() {
 * 			public void handleMessage(Message msg) {
 * 				// process incoming messages here
 * 			}
 * 		};
 *
 * 		Looper.loop();
 * 	}
 * }
 * </pre>
 */
public final class Looper {
	/*
	 * API Implementation Note:
	 *
	 * This class contains the code required to set up and manage an event loop
	 * based on MessageQueue. APIs that affect the state of the queue should be
	 * defined on MessageQueue or Handler rather than on Looper itself. For
	 * example, idle handlers and sync barriers are defined on the queue whereas
	 * preparing the thread, looping, and quitting are defined on the looper.
	 */

//	private static final String TAG = "Looper";

	// sThreadLocal.get() will return null unless you've called prepare().
	static final ThreadLocal<Looper> sThreadLocal = new ThreadLocal<Looper>();
	private static Looper sMainLooper; // guarded by Looper.class

	final MessageQueue mQueue;
	final Thread mThread;

	/**
	 * Initialize the current thread as a looper. This gives you a chance to
	 * create handlers that then reference this looper, before actually starting
	 * the loop. Be sure to call {@link #loop()} after calling this method, and
	 * end it by calling {@link #quit()}.
	 */
	public static void prepare() {
		prepare(true);
	}

	private static void prepare(boolean quitAllowed) {
		if (sThreadLocal.get() != null) {
			throw new RuntimeException("Only one Looper may be created per thread");
		}
		sThreadLocal.set(new Looper(quitAllowed));
	}

	/**
	 * Initialize the current thread as a looper, marking it as an application's
	 * main looper. The main looper for your application is created by the
	 * Android environment, so you should never need to call this function
	 * yourself. See also: {@link #prepare()}
	 */
	public static void prepareMainLooper() {
		prepare(false);
		synchronized (Looper.class) {
			if (sMainLooper != null) {
				throw new IllegalStateException("The main Looper has already been prepared.");
			}
			sMainLooper = myLooper();
		}
	}

	/**
	 * Returns the application's main looper, which lives in the main thread of
	 * the application.
	 */
	public static Looper getMainLooper() {
		synchronized (Looper.class) {
			return sMainLooper;
		}
	}

	/**
	 * Run the message queue in this thread. Be sure to call {@link #quit()} to
	 * end the loop.
	 */
	public static void loop() {
		final Looper me = myLooper();
		if (me == null) {
			throw new RuntimeException("No Looper; Looper.prepare() wasn't called on this thread.");
		}
		final MessageQueue queue = me.mQueue;

		// Make sure the identity of this thread is that of the local process,
		// and keep track of what that identity token actually is.
		for (;;) {
			Message msg = queue.next(); // might block
			if (msg == null) {
				// No message indicates that the message queue is quitting.
				return;
			}
			try {
				msg.target.dispatchMessage(msg);
			} finally {

			}
			// Make sure that during the course of dispatching the
			// identity of the thread wasn't corrupted.
			msg.recycleUnchecked();
		}
	}

	/**
	 * Return the Looper object associated with the current thread. Returns null
	 * if the calling thread is not associated with a Looper.
	 */
	public static  Looper myLooper() {
		return sThreadLocal.get();
	}

	/**
	 * Return the {@link MessageQueue} object associated with the current
	 * thread. This must be called from a thread running a Looper, or a
	 * NullPointerException will be thrown.
	 */
	public static  MessageQueue myQueue() {
		return myLooper().mQueue;
	}

	private Looper(boolean quitAllowed) {
		mQueue = new MessageQueue(quitAllowed);
		mThread = Thread.currentThread();
	}

	/**
	 * Returns true if the current thread is this looper's thread.
	 */
	public boolean isCurrentThread() {
		return Thread.currentThread() == mThread;
	}


	/**
	 * Quits the looper safely.
	 * <p>
	 * Causes the {@link #loop} method to terminate as soon as all remaining
	 * messages in the message queue that are already due to be delivered have
	 * been handled. However pending delayed messages with due times in the
	 * future will not be delivered before the loop terminates.
	 * </p>
	 * <p>
	 * Any attempt to post messages to the queue after the looper is asked to
	 * quit will fail. For example, the {@link Handler#sendMessage(Message)}
	 * method will return false.
	 * </p>
	 */
	public void quitSafely() {
		mQueue.quit();
	}

	/**
	 * Gets the Thread associated with this Looper.
	 *
	 * @return The looper's thread.
	 */
	public  Thread getThread() {
		return mThread;
	}

	/**
	 * Gets this looper's message queue.
	 *
	 * @return The looper's message queue.
	 */
	public  MessageQueue getQueue() {
		return mQueue;
	}
	
	@Override
	public String toString() {
		return "Looper (" + mThread.getName() + ", tid " + mThread.getId() + ") {"
				+ Integer.toHexString(System.identityHashCode(this)) + "}";
	}
}