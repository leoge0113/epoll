package com.wizzardo.epoll;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.wizzardo.epoll.Utils.readInt;

/**
 * @author: wizzardo
 * Date: 6/25/14
 */
public class IOThread<T extends Connection> extends EpollCore<T> {
    private static AtomicInteger number = new AtomicInteger();

    private long ttl = 30000;
    private T[] connections;
    private LinkedHashMap<Long, T> timeouts = new LinkedHashMap<Long, T>();
    private AtomicInteger connectionsCounter = new AtomicInteger();

    public IOThread() {
        setName("IOThread-" + number.incrementAndGet());
    }

    @Override
    public void run() {
        byte[] events = new byte[this.events.capacity()];
//        System.out.println("start new ioThread");

        while (running) {
            try {
                this.events.position(0);
                Long now = System.nanoTime() * 1000;
                int r = waitForEvents(500);
//                System.out.println("events length: "+r);
                this.events.limit(r);
                this.events.get(events, 0, r);
                int i = 0;
//                eventCounter.addAndGet(r / 5);
                while (i < r) {
                    int event = events[i];
                    i++;
                    int fd = readInt(events, i);
//                    System.out.println("event on fd " + fd + ": " + event);
                    i += 4;
                    T connection = null;
                    switch (event) {
                        case 1: {
                            connection = getConnection(fd);
                            if (connection == null) {
                                connection.close();
                                continue;
                            } else
                                onRead(connection);
                            break;
                        }
                        case 2: {
                            connection = getConnection(fd);
                            if (connection == null) {
                                connection.close();
                                continue;
                            } else
                                onWrite(connection);
                            break;
                        }
                        case 3: {
                            connection = getConnection(fd);
                            deleteConnection(fd);
                            if (connection == null)
                                continue;
                            connection.close();
                            continue;
                        }
                    }
                    Long key = connection.setLastEvent(now);
                    timeouts.put(now++, connection);
                    if (key != null)
                        timeouts.remove(key);
                }

                handleTimeOuts(now);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    private void handleTimeOuts(Long eventTime) {
        eventTime -= ttl * 1000000L * 1000;
        T connection;
        Map.Entry<Long, T> entry;

        Iterator<Map.Entry<Long, T>> iterator = timeouts.entrySet().iterator();
        while (iterator.hasNext()) {
            entry = iterator.next();
            if (entry.getKey() > eventTime)
                break;

            iterator.remove();
            if (entry.getValue().isInvalid(eventTime)) {
                connection = deleteConnection(entry.getValue().fd);
                if (connection != null)
                    close(connection);
            }
        }
    }

    private T getConnection(int fd) {
        return connections[fd];
    }

    private T deleteConnection(int fd) {
        T connection = connections[fd];
        connections[fd] = null;
        return connection;
    }

    protected void putConnection(T connection, Long eventTime) {
        if (connections == null || connections.length <= connection.fd) {
            T[] array = (T[]) Array.newInstance(connection.getClass(), connection.fd * 3 / 2);
            if (connections != null)
                System.arraycopy(connections, 0, array, 0, connections.length);
            connections = array;
        }
        connections[connection.fd] = connection;
        connectionsCounter.incrementAndGet();

        connection.setIOThread(this);
        attach(scope, connection.fd);
        connection.setLastEvent(eventTime);
        onConnect(connection);
    }

    public void close(T connection) {
        connection.setIsAlive(false);
        close(connection.fd);
        connectionsCounter.decrementAndGet();
        onDisconnect(connection);
    }

    public int getConnectionsCount() {
        return connectionsCounter.get();
    }

    public void onRead(T connection) {
    }

    public void onWrite(T connection) {
        connection.write();
    }

    public void onConnect(T connection) {
    }

    public void onDisconnect(T connection) {
    }
}