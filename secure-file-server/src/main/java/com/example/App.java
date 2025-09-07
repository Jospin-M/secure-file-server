package com.example;

import java.net.InetSocketAddress;

import javax.net.ssl.SSLContext;
import com.sun.net.httpserver.*;

public class App {
    public static void main(String[] args) throws Exception {
        ServerInitializer initializer = new ServerInitializer();
        SSLContext sslContext = initializer.createSSLContext();
        HttpsServer server = initializer.createConfiguredHttpsServer(sslContext, 8443, 0);
        server.start();
        
        InetSocketAddress address = server.getAddress();
        System.out.println("Server started on " + address.getHostString());
    }
}
