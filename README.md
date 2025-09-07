# secure-file-server

## Certificate Generation

This project requires TLS certificates to run the HTTPS server. 
For development, you can generate self-signed certificates.

**Note**: Self-signed certificates are only suitable for development. Browsers will show security warnings.

## Prerequisites
- OpenSSL installed on your system
- Basic understanding of TLS/SSL certificates

### Generate Certificate

1. **Generate a private key**
   ```bash
   openssl genrsa -out certs/server.key 2048
   ```

2. **Create a Certificate Signing Request (CSR)**
   ```bash
   openssl req -new -key certs/server.key -out certs/server.csr \
     -subj "/C=US/ST=State/L=City/O=Organization/CN=localhost"
   ```

3. **Generate a self-signed certificate**
   ```bash
   openssl x509 -req -in certs/server.csr -signkey certs/server.key \
     -out certs/server.crt -days 365
   ```

4. **Create a PKCS#12 keystore for Java**
   ```bash
   openssl pkcs12 -export \
     -in certs/server.crt \
     -inkey certs/server.key \
     -out certs/keystore.p12 \
     -name myserver
   ```
   
   You'll be prompted to set an export password. **Save this password in a .env file in the following format: key_store_password=[yourpassword] **.

## Verification

```bash
# Check certificate details
openssl x509 -in certs/server.crt -text -noout

# Verify certificate matches private key
openssl x509 -noout -modulus -in certs/server.crt | openssl md5
openssl rsa -noout -modulus -in certs/server.key | openssl md5
# The MD5 hashes should match
```

### Test HTTPS Connection

```bash
# Test with OpenSSL
openssl s_client -connect localhost:8443 -servername localhost
```
