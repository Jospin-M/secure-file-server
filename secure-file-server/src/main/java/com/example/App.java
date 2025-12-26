package com.example;

import java.net.InetSocketAddress;

import javax.net.ssl.SSLContext;

import com.sun.net.httpserver.*;
import com.example.handlers.UploadHandler;
import com.example.handlers.DownloadHandler;

public class App {
    public static void main(String[] args) throws Exception {
        ServerInitializer initializer = new ServerInitializer();
        SSLContext sslContext = initializer.createSSLContext();
        
        HttpsServer server = ServerInitializer.createConfiguredHttpsServer(sslContext, 8443, 0);
        server.createContext("/download", new DownloadHandler());
        server.createContext("/upload", new UploadHandler());
        server.start();
        
        InetSocketAddress address = server.getAddress();
        System.out.println("Server started on " + address.getHostString());
    }
}
