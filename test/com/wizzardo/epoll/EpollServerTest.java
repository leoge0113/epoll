package com.wizzardo.epoll;


import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author: wizzardo
 * Date: 1/4/14
 */
public class EpollServerTest {

    @Test
    public void startStopTest() throws InterruptedException {
        EpollServer server = new EpollServer() {

            @Override
            protected Connection createConnection(int fd, int ip, int port) {
                return new Connection(fd, ip, port);
            }

            @Override
            public void readyToRead(Connection connection) {
            }

            @Override
            public void readyToWrite(Connection connection) {
            }

            @Override
            public void onOpenConnection(Connection connection) {
            }

            @Override
            public void onCloseConnection(Connection connection) {
            }
        };
        int port = 9091;

        server.bind(port);
        server.start();

        Thread.sleep(500);

        server.stopServer();

        Thread.sleep(510);

        String connectionRefuse = null;
        try {
            new Socket("localhost", port);
        } catch (IOException e) {
            connectionRefuse = e.getMessage();
        }
        Assert.assertEquals("Connection refused", connectionRefuse);
    }

    @Test
    public void echoTest() throws InterruptedException {
        EpollServer server = new EpollServer() {

            @Override
            protected Connection createConnection(int fd, int ip, int port) {
                return new Connection(fd, ip, port);
            }

            @Override
            public void readyToRead(Connection connection) {
                try {
                    byte[] b = new byte[1024];
                    int r = read(connection, b, 0, b.length);
                    int w = 0;
                    while (w < r) {
                        w += write(connection, b, w, r - w);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void readyToWrite(Connection connection) {
            }

            @Override
            public void onOpenConnection(Connection connection) {
            }

            @Override
            public void onCloseConnection(Connection connection) {
            }
        };
        int port = 9090;

        server.bind(port);
        server.start();

        try {
            Socket s = new Socket("localhost", port);
            OutputStream out = s.getOutputStream();
            out.write("hello world!".getBytes());

            InputStream in = s.getInputStream();
            byte[] b = new byte[1024];
            int r = in.read(b);

            Assert.assertEquals("hello world!", new String(b, 0, r));
        } catch (IOException e) {
            e.printStackTrace();
        }
        server.stopServer();
    }

//    @Test
    public void httpTest() throws InterruptedException {
        EpollServer server = new EpollServer() {

            byte[] response = "HTTP/1.1 200 OK\r\nConnection: Keep-Alive\r\nContent-Length: 5\r\nContent-Type: text/html;charset=UTF-8\r\n\r\nololo".getBytes();
//            byte[] response = "HTTP/1.1 200 OK\r\nConnection: Close\r\nContent-Length: 5\r\nContent-Type: text/html;charset=UTF-8\r\n\r\nololo".getBytes();

            @Override
            protected Connection createConnection(int fd, int ip, int port) {
                return new Connection(fd, ip, port);
            }

            @Override
            public void readyToRead(Connection connection) {
                try {
                    byte[] b = new byte[1024];
                    int r = read(connection, b, 0, b.length);
//                    System.out.println(new String(b,0,r));
                    int w = 0;
                    while (w < response.length) {
                        w += write(connection, response, w, response.length - w);
                    }
//                    close(connection);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void readyToWrite(Connection connection) {
            }

            @Override
            public void onOpenConnection(Connection connection) {
            }

            @Override
            public void onCloseConnection(Connection connection) {
            }
        };
        int port = 9090;

        server.bind(port);
        server.start();

        Thread.sleep(5 * 60 * 1000);

        server.stopServer();
    }

    @Test
    public void maxEventsTest() throws InterruptedException {
        EpollServer server = new EpollServer() {

            @Override
            protected Connection createConnection(int fd, int ip, int port) {
                return new Connection(fd, ip, port);
            }

            @Override
            public void readyToRead(Connection connection) {
                try {
                    byte[] b = new byte[1024];
                    int r = read(connection, b, 0, b.length);
                    int w = 0;
                    while (w < r) {
                        w += write(connection, b, w, r - w);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void readyToWrite(Connection connection) {
            }

            @Override
            public void onOpenConnection(Connection connection) {
//                System.out.println(connection.fd);
            }

            @Override
            public void onCloseConnection(Connection connection) {
            }
        };
        final int port = 9092;

        server.bind(port, 2);
        server.start();

        final AtomicLong total = new AtomicLong(0);
        long time = System.currentTimeMillis();

        int threads = 512;
        final int n = 1000;
        final CountDownLatch latch = new CountDownLatch(threads);


        for (int j = 0; j < threads; j++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket s = new Socket("localhost", port);
                        OutputStream out = s.getOutputStream();
                        InputStream in = s.getInputStream();
                        byte[] b = new byte[1024];
                        for (int i = 0; i < n; i++) {
                            out.write("hello world!".getBytes());

                            int r = in.read(b);
                            total.addAndGet(r);

                            Assert.assertEquals("hello world!", new String(b, 0, r));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                }
            }).start();

        }
        latch.await();
        System.out.println("total bytes were sent: " + total.get() * 2);
        time = System.currentTimeMillis() - time;
        System.out.println("for " + time + "ms");
        System.out.println(total.get() * 1000.0 / time / 1024.0 / 1024.0);
        server.stopServer();
    }
}