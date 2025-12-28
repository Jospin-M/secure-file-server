package com.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.net.ssl.SSLContext;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpsServer;

import com.example.security.TokenStore;
import com.example.handlers.UploadHandler;
import com.example.security.Authenticator;
import com.example.security.OwnershipStore;
import com.example.server.ServerInitializer;
import com.example.handlers.DownloadHandler;

public class App {
    private static final Path CONFIG_DIR = Paths.get("config");

    public static void main(String[] args) throws Exception {
        ServerInitializer initializer = new ServerInitializer();
        SSLContext sslContext = initializer.createSSLContext();
        
        Files.createDirectories(CONFIG_DIR); // create the config directory if it doesn't exist yet
        Path tokenFile = CONFIG_DIR.resolve("tokens.txt");
        TokenStore tokenStore = new TokenStore(tokenFile);
        Authenticator authenticator = new Authenticator(tokenStore);
        
        Path ownerFile = CONFIG_DIR.resolve("ownership.txt");
        OwnershipStore ownershipStore = new OwnershipStore(ownerFile);

        HttpsServer server = ServerInitializer.createConfiguredHttpsServer(sslContext, 8443, 0);
        server.createContext("/download", new DownloadHandler(authenticator, ownershipStore));
        server.createContext("/upload", new UploadHandler(authenticator, ownershipStore));
        server.start();
        
        InetSocketAddress address = server.getAddress();
        System.out.println("Server started on " + address.getHostString());
    }
}
