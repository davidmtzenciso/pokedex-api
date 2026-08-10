package com.elatusdev.pokedex.infrastructure.security;

import com.elatusdev.pokedex.domain.port.TokenIssuer;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// The private half never leaves this process and the keystore password never leaves the
// environment. A missing keystore fails the boot rather than falling back to a generated
// key: a key nobody chose is a key nobody can rotate.
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {

    @Bean
    TokenIssuer tokenIssuer(JwtProperties properties) throws GeneralSecurityException, IOException {
        char[] password = properties.keystorePassword().toCharArray();
        KeyStore keystore = KeyStore.getInstance("PKCS12");
        try (InputStream source = properties.keystorePath().getInputStream()) {
            keystore.load(source, password);
        }
        Certificate certificate = requireEntry(keystore, properties);
        PrivateKey signingKey = (PrivateKey) keystore.getKey(properties.keyAlias(), password);
        return new Es256TokenIssuer(signingKey, certificate.getPublicKey(), properties);
    }

    private static Certificate requireEntry(KeyStore keystore, JwtProperties properties)
            throws GeneralSecurityException {
        Certificate certificate = keystore.getCertificate(properties.keyAlias());
        if (certificate == null) {
            throw new IllegalStateException(
                    "keystore " + properties.keystorePath() + " has no entry for alias " + properties.keyAlias());
        }
        return certificate;
    }
}
