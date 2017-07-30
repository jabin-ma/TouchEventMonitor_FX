package com.android.ddmlib.input;

import com.android.ddmlib.Log;

import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by majipeng on 2017/6/19.
 * 负责将从Eventhub中读到的原始数据解析封装成本地数据
 */
public class EventHubReader implements Callable<Void> {

    private HashMap<String, EventMapper> mappers = new HashMap<>();

    private KnownEventList knownEventList = new KnownEventList();

    private EventHub eventHub;

    private BlockingQueue<MonitorEvent> mMappedEvent = new LinkedBlockingQueue<>(2);


    private static final String TAG = "EventHubReader";


    public EventHubReader(EventHub eventHub) {
        this.eventHub = eventHub;
    }

    /**
     * 同步方法,从若干RawEvent中映射一个本地事件,调用此方法将会从eventhub中读取原始数据,直到出现一次完整本地事件
     *
     * @return null eventHub出现问题  !null 解析到一次完整事件,返回
     */
    MonitorEvent readAndMapping() {
        MonitorEvent result = null;
        while (!Thread.interrupted()) {
            PlainTextRawEvent rawEvent = eventHub.takeRawEvent(); //从eventHub中读取数据
            if (rawEvent == null) {//如果是空,那么表示eventHub出现问题(如device退出)
                Log.d(TAG, "eventhub mapping rawevent null!!");
                break;//跳出此次事件解析 直接返回
            }
            EventMapper mapper = mappers.get(rawEvent.getDevFile());
            if (mapper == null) {
                mapper = new EventMapperImpl(knownEventList);
                mappers.put(rawEvent.getDevFile(), mapper);
            }
            result = mapper.mappingEvent(rawEvent);
            if (result == null) continue;
            else break;//不是一次完整的事件,继续读取
        }
        return result;
    }


    @Override
    public Void call() throws Exception {
        while (!Thread.interrupted()) {
            MonitorEvent monitorEvent = readAndMapping();
            if (monitorEvent != null) {//
                Log.d(TAG, "mapping:" + monitorEvent);
//                inputDispatcher.dispatchEvent(monitorEvent);
                mMappedEvent.add(monitorEvent);
            } else {
                Log.d(TAG, "mapping null .. exit!!");
                break;
            }
        }
        Log.d(TAG,"run finish");
        return null;
    }


    public MonitorEvent takeMappedEvent() throws InterruptedException {
        return mMappedEvent.take();
    }
}
