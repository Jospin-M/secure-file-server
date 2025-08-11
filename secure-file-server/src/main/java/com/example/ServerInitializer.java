package com.example;

import java.io.FileInputStream;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * ServerInitializer is responsible for setting up the SSL/TLS configuration
 * for a secure server.
 * 
 * It loads a PKCS#12 keystore file containing the server's private key
 * and certificate, and uses it to initialize an SSLContext that can be
 * used to create secure sockets.
 */

public class ServerInitializer {
    private char[] keyStorePassword;

    /**
     * Loads the keystore containing the server's certificate and private key.
     * 
     * @return The loaded KeyStore instance.
     * @throws Exception If there is an error loading the keystore.
     */
    private KeyStore createKeystore() throws Exception {
        Dotenv dotenv = Dotenv.load();
        keyStorePassword = dotenv.get("key_store_password").toCharArray();

        // Load the keystore with the certificate and private key store in the PKCS#12 file
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        
        try(FileInputStream fis = new FileInputStream("certs\\keystore.p12")) {
            keyStore.load(fis, keyStorePassword);
        } 

        return keyStore;
    }

     /**
     * Creates and initializes an SSLContext for the TLS protocol.
     * 
     * The SSLContext will be configured with the server's private key and certificate,
     * enabling it to present its identity to clients during the TLS handshake.
     * 
     * @return A fully initialized SSLContext ready to create secure server sockets.
     * @throws Exception If initialization fails.
     */
    public SSLContext createSSLContext() throws Exception {
        // Create a KeyManagerFactory to manage the server's keys
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        
        // Initialize the KeyManagerFactory with the loaded keystore and password
        kmf.init(createKeystore(), keyStorePassword);

       // Create an SSLContext using the TLS protocol
        SSLContext sslContext = SSLContext.getInstance("TLS");

        // Initialize the SSLContext with the server's key managers
        sslContext.init(kmf.getKeyManagers(), null, null);

        return sslContext;
    }
}
