package com.android.ddmlib.input;

import com.android.ddmlib.utils.Log;

import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by majipeng on 2017/6/19.
 * 负责将从Eventhub中读到的原始数据解析封装成本地数据
 */
class EventHubReader implements Callable<Void> {
    //每个设备的映射器(dev/input/*),有几个输入设备就会存在几个映射器
    private HashMap<String, EventMapper> mappers = new HashMap<>();
    //映射规则,映射器将根据该文件描述生成对应的MonitorEvent
    private KnownEventList knownEventList = new KnownEventList();
    //事件总线,所有事件都可以从这里读取到,起一个汇总的作用
    private EventHub eventHub;
    //映射完后的事件将会保存在这里,需要时 调用@takeMappedEvent()方法获取映射完成的事件
    private BlockingQueue<MonitorEvent> mMappedEvent = new LinkedBlockingQueue<>(1024);

    private static final String TAG = "EventHubReader";
    private static final boolean DEBUG = false;

    public EventHubReader(EventHub eventHub) {
        this.eventHub = eventHub;
    }

    /**
     * 同步方法,从若干RawEvent中映射一个本地事件,调用此方法将会从eventhub中读取原始数据,直到出现一次完整本地事件
     *
     * @return null eventHub出现问题  !null 解析到一次完整事件,返回
     */
    private MonitorEvent readAndMapping() {
        MonitorEvent result = null;
        while (!Thread.interrupted()) {
            IRawEvent rawEvent = eventHub.takeRawEvent(); //从eventHub中读取数据
            if (rawEvent == null) {//如果是空,那么表示eventHub出现问题(如device退出)
                Log.d(TAG, "eventhub mapping rawevent null!!");
                break;//跳出此次事件解析 直接返回
            }
            EventMapper mapper = mappers.get(rawEvent.getOwner());
            if (mapper == null) {
                mapper = new EventMapperImpl(knownEventList);
                mappers.put(rawEvent.getOwner(), mapper);
            }
            //@TODO 需要使用多线程以保证每个输入设备之间不会相互影响
            result = mapper.mappingEvent(rawEvent);
            if (result == null) continue;
            else break;//不是一次完整的事件,继续读取
        }
        return result;
    }


    @Override
    public Void call() {
        try {
            while (!Thread.interrupted()) {
                MonitorEvent monitorEvent = readAndMapping();
                if (monitorEvent != null) {//
                    if (DEBUG) Log.d(TAG, "mapping:" + monitorEvent);
                    mMappedEvent.add(monitorEvent);
                } else {
                    Log.e(TAG, "mapping null .. exit!!");
                    break;
                }
            }
        } catch (Exception e) {
//            e.printStackTrace();
        } finally {
            Log.d(TAG, "run finish");
        }
        return null;
    }

    /**
     * 获取一个已经映射完成的事件
     *
     * @return 映射完成的事件, 不为空
     * @throws InterruptedException
     */
    public MonitorEvent takeMappedEvent() throws InterruptedException {
        return mMappedEvent.take();
    }
}
