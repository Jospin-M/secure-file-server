package com.example;

import io.github.cdimascio.dotenv.Dotenv;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;

public class App {
    public static void main(String[] args) throws Exception {
        Dotenv dotenv = Dotenv.load();
        char[] keyStorePassword = dotenv.get("key_store_password").toCharArray();

        // 1. Load the keystore with the certificate and private key store in the PKCS#12 file
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        
        try(FileInputStream fis = new FileInputStream("secure-file-server\\certs\\keystore.p12")) {
            keyStore.load(fis, keyStorePassword);
        } 

        // 2. Initialize key managers which will supply the private key and certificate to the server
        // so that it can verify its identity to clients
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyStorePassword);

        // 3. Initialize SSLContext - creates an object that ensures communication between client and server is 
        // encrypted
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
    }
}
