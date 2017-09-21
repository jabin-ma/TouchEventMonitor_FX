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

import com.android.ddmlib.utils.Log;

import java.lang.reflect.Modifier;

@SuppressWarnings("unchecked")
public class Handler<T> {

    private static final boolean FIND_POTENTIAL_LEAKS = false;
    private static final String TAG = "Handler";


    public interface Callback<T> {
        boolean handleMessage(Message<T> msg) throws InterruptedException;
    }


    public void handleMessage(Message<T> msg) throws InterruptedException {
    }

    void dispatchMessage(Message<T> msg) throws InterruptedException {
        if (msg.callback != null) {
            handleCallback(msg);
        } else {
            if (mCallback != null) {
                if (mCallback.handleMessage(msg)) {
                    return;
                }
            }
            handleMessage(msg);
        }
    }


    public Handler() {
        this(null, false);
    }

    public Handler(Callback<T> callback) {
        this(callback, false);
    }


    public Handler(Looper looper) {
        this(looper, null, false);
    }


    public Handler(Looper looper, Callback callback) {
        this(looper, callback, false);
    }


    public Handler(boolean async) {
        this(null, async);
    }


    public Handler(Callback callback, boolean async) {
        if (FIND_POTENTIAL_LEAKS) {
            final Class<? extends Handler> klass = getClass();
            if ((klass.isAnonymousClass() || klass.isMemberClass() || klass.isLocalClass())
                    && (klass.getModifiers() & Modifier.STATIC) == 0) {
                Log.w(TAG, "The following Handler class should be static or leaks might occur: "
                        + klass.getCanonicalName());
            }
        }

        mLooper = Looper.myLooper();
        if (mLooper == null) {
            throw new RuntimeException("Can't create handler inside thread that has not called Looper.prepare()");
        }
        mQueue = mLooper.mQueue;
        mCallback = callback;
        mAsynchronous = async;
    }


    public Handler(Looper looper, Callback callback, boolean async) {
        mLooper = looper;
        mQueue = looper.mQueue;
        mCallback = callback;
        mAsynchronous = async;
    }


    public String getTraceName(Message message) {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName()).append(": ");
        if (message.callback != null) {
            sb.append(message.callback.getClass().getName());
        } else {
            sb.append("#").append(message.what);
        }
        return sb.toString();
    }


    public String getMessageName(Message<T> message) {
        if (message.callback != null) {
            return message.callback.getClass().getName();
        }
        return "0x" + Integer.toHexString(message.what);
    }

    public final Message<T> obtainMessage() {
        return Message.obtain(this);
    }


    public final Message<T> obtainMessage(int what) {
        return Message.obtain(this, what);
    }


    public final Message<T> obtainMessage(int what, T obj) {
        return Message.obtain(this, what, obj);
    }

    public final Message<T> obtainMessage(int what, int arg1, int arg2) {
        return Message.obtain(this, what, arg1, arg2);
    }


    public final Message<T> obtainMessage(int what, int arg1, int arg2, T obj) {
        return Message.obtain(this, what, arg1, arg2, obj);
    }

    public final boolean post(Runnable r) {
        return sendMessageDelayed(getPostMessage(r), 0);
    }

    public final boolean postDelayed(Runnable r, long delayMillis) {
        return sendMessageDelayed(getPostMessage(r), delayMillis);
    }


    public final boolean sendMessage(Message<T> msg) {
        return sendMessageDelayed(msg, 0);
    }


    public final boolean sendEmptyMessage(int what) {
        return sendEmptyMessageDelayed(what, 0);
    }


    public final boolean sendEmptyMessageDelayed(int what, long delayMillis) {
        Message msg = Message.obtain();
        msg.what = what;
        return sendMessageDelayed(msg, delayMillis);
    }

    public final boolean sendMessageDelayed(Message<T> msg, long delayMillis) {
        if (delayMillis < 0) {
            delayMillis = 0;
        }
        return enqueueMessage(mQueue, msg, delayMillis);
    }


    private boolean enqueueMessage(MessageQueue<T> queue, Message<T> msg, long uptimeMillis) {
        msg.target = this;
        if (mAsynchronous) {
            msg.setAsynchronous(true);
        }
        return queue.enqueueMessage(msg, uptimeMillis);
    }


    public final Looper getLooper() {
        return mLooper;
    }

    @Override
    public String toString() {
        return "Handler (" + getClass().getName() + ") {" + Integer.toHexString(System.identityHashCode(this)) + "}";
    }

    private static Message getPostMessage(Runnable r) {
        Message m = Message.obtain();
        m.callback = r;
        return m;
    }

    private static void handleCallback(Message message) {
        message.callback.run();
    }


    final Looper mLooper;
    final MessageQueue<T> mQueue;
    final Callback<T> mCallback;
    final boolean mAsynchronous;


}
