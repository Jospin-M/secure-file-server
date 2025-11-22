package com.example;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Responsible for setting up the SSL/TLS configuration for a secure server.
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

    /**
     * Creates an HTTPS server bound to the given port and configured with the provided SSLContext.
     * 
     * @param sslContext the initialized {@link javax.net.ssl.SSLContext} instance containing the server's
     * SSL/TLS configuration which includes the key managers to be used for encrypted communication.
     * 
     * @param port the TCP port number on which the HTTPS server will listen for incoming connections. A
     * value of {@code 0} will assign an automatically available port.
     * 
     * @param backlog the maximum number of incoming connection requests that can be queued by the operating
     * system before being accepted by the server. If the queue is full, new incoming connections may be refused.
     */
    public static HttpsServer createConfiguredHttpsServer(SSLContext sslContext, int port, int backlog) throws IOException {
        HttpsServer server = HttpsServer.create(new InetSocketAddress(port), backlog);

        server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                try {
                    // Use the SSLContext to obtain default parameters and an engine.
                    SSLContext ctx = getSSLContext();
                    SSLEngine engine = ctx.createSSLEngine();
                    engine.setUseClientMode(false);

                    // Disable the need for clients to have certs.
                    SSLParameters sslParams = ctx.getDefaultSSLParameters();
                    sslParams.setNeedClientAuth(false)
                    ;
                    // Use engine defaults for cipher suites and protocols.
                    sslParams.setCipherSuites(engine.getEnabledCipherSuites());
                    sslParams.setProtocols(engine.getEnabledProtocols());

                    // Apply our custom protocols to the server
                    params.setSSLParameters(sslParams);
                } catch(Exception e) {
                    // Fail fast if configuration cannot be applied
                    throw new RuntimeException("Failed to configure HTTPS parameters", e);
                }
            } 
        });

        return server;
    }
}
