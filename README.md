# Secure File Server

A lightweight, TLS-encrypted HTTPS file server built in Java for secure file serving and API protection.

---

## Overview

The Secure File Server is a Java-based HTTPS server that demonstrates secure file serving over TLS with token-based authentication. It serves as both a learning resource for secure server implementation and a foundation for custom secure file-serving applications.

---

## Features

- **TLS/SSL Encryption**: All communication encrypted using self-signed certificates
- **Token-Based Authentication**: API protection via configurable access tokens
- **Environment-Based Configuration**: Secure credential management using `.env` files

---

## Use Cases

- Secure file serving in local or test environments
- Learning Java-based TLS/SSL server implementation
- Understanding keystore, certificate, and SSLContext integration
- Prototyping secure APIs with token-based authentication

---

## Prerequisites

- JDK 17 or higher
- Maven 3.6+
- Bash shell

---

## Quick Start

### 1. Clone the Repository
```bash
git clone 
cd secure-file-server
```

### 2. Generate TLS Certificates

Run the certificate generation script:
```bash
./generate-cert
```

This creates a PKCS#12 keystore (`keystore.p12`) in the `certs/` directory containing:
- A self-signed TLS certificate
- The associated private key

### 3. Configure Environment Variables

Create a `.env` file in the project root:
```bash
key_store_password=your_secure_password_here
```

### 4. Start the Server
```bash
./run-server.sh
```

The server will start and listen for HTTPS requests on the configured port.

---

## Authentication

### Token-Based Access Control

All API endpoints require valid authentication tokens. To grant access:

1. Add token-user pairs to `config/tokens.txt`
2. Format: `access_token|user_id` (one pair per line)
3. Include the token in client requests (e.g curl -k -H "Authorization: Bearer [token]" https://[domain]/[endpoint]/[file]"


Requests without valid tokens will be rejected with a `401 Unauthorized` response.

---
