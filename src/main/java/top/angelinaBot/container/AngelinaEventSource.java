package top.angelinaBot.container;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.angelinaBot.model.AngelinaMessageEvent;
import top.angelinaBot.model.MessageInfo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
public class AngelinaEventSource {


    private static volatile AngelinaEventSource instance;

    //构造方法私有化
    private AngelinaEventSource(){}

    //双重校验锁单例模式
    public static AngelinaEventSource getInstance() {
        if (instance == null) {
            synchronized (AngelinaEventSource.class) {
                if (instance == null) {
                    instance = new AngelinaEventSource();
                }
            }
        }
        return instance;
    }

    //listener集合私有化
    private final Map<AngelinaListener, AngelinaMessageEvent> listenerMap = new HashMap<>();

    public static AngelinaMessageEvent waiter(AngelinaListener listener) {
        //waiter方法：将listener加入map中，然后挂起方法
        AngelinaMessageEvent o = new AngelinaMessageEvent();
        AngelinaEventSource.getInstance().registerEventListener(listener, o);
        synchronized (o.getLock()) {
            log.info("线程等待");
            try {
                o.getLock().wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return o;
    }

    public void registerEventListener(AngelinaListener listener, AngelinaMessageEvent o) {
        if (listener != null) {
            listenerMap.put(listener, o);
        }
    }

    public void handle(MessageInfo message) {
        //当收到消息时，遍历所有listener
        for (Iterator<AngelinaListener> it = listenerMap.keySet().iterator(); it.hasNext();){
            AngelinaListener l = it.next();
            AngelinaMessageEvent event = listenerMap.get(l);
            Integer time = l.getSecond();
            if ((System.currentTimeMillis() - l.timestamp) / 1000 > time) {
                //判断时间是否超时
                synchronized (event.getLock()) {
                    log.warn("线程超时");
                    event.getLock().notify();
                }
                it.remove();
            } else if (l.callback(message)) {
                //调用callback方法，如果返回true则唤起线程
                synchronized (event.getLock()) {
                    log.info("唤起线程");
                    event.setMessageInfo(message);
                    event.getLock().notify();
                }
                it.remove();
            }
        }
    }

    public static void remove(Long groupId) {
        AngelinaEventSource.getInstance().listenerMap.keySet().removeIf(l -> {
            String className = null;
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (StackTraceElement s: stackTrace) {
                try {
                    Class<?> c = Class.forName(s.getClassName());
                    if (c.getAnnotation(Service.class) != null) {
                        className = s.getClassName();
                        break;
                    }
                } catch (ClassNotFoundException e) {
                    className = Thread.currentThread().getStackTrace()[2].getClassName();
                    e.printStackTrace();
                }
            }
            return groupId.equals(l.getGroupId()) && l.className.equals(className);
        });
    }

    //专为临时会话的listener
    public Map<AngelinaListener, AngelinaMessageEvent> listenerSet2 = new HashMap<>();

    public static AngelinaMessageEvent waiter2(AngelinaListener listener) {
        AngelinaMessageEvent o = new AngelinaMessageEvent();
        AngelinaEventSource.getInstance().registerEventListener2(listener, o);
        synchronized (o.getLock()) {
            log.info("线程等待");
            try {
                o.getLock().wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return o;
    }



    public void registerEventListener2(AngelinaListener listener, AngelinaMessageEvent o) {
        if (listener != null) {
            listenerSet2.put(listener, o);
            //由于临时会话数量较少，直接开辟子线程进行延时控制，还存在就删掉listener
            new Thread(() ->{
                try {
                    Integer time = listener.getSecond();
                    Thread.sleep(time*1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(listenerSet2.containsKey(listener)) {
                    synchronized (o.getLock()) {
                        log.warn("线程超时");
                        o.getLock().notify();
                    }
                    listenerSet2.remove(listener);
                }
            }).start();
        }
    }

    public void handle2(MessageInfo message) {
        for(Iterator<AngelinaListener> it = listenerSet2.keySet().iterator(); it.hasNext();){
            AngelinaListener l = it.next();
            AngelinaMessageEvent event = listenerSet2.get(l);
            Integer time = l.getSecond();
            if ((System.currentTimeMillis() - l.timestamp) / 1000 < time) {
                synchronized (event.getLock()) {
                    log.info("唤起线程");
                    event.setMessageInfo(message);
                    event.getLock().notify();
                }
                it.remove();
            }
        }
    }

}


