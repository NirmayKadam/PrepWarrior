package com.example.mockinterview.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseApp firebaseApp() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        String base64Config = System.getenv("FIREBASE_CONFIG_B64");
        if (base64Config == null || base64Config.trim().isEmpty()) {
            logger.warn("FIREBASE_CONFIG_B64 environment variable not set. Firebase Admin SDK NOT initialized.");
            return null;
        }

        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Config.trim());
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(decodedBytes)))
                    .build();
            logger.info("Initializing Firebase Admin SDK...");
            return FirebaseApp.initializeApp(options);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to decode base64 Firebase configuration", e);
            return null;
        } catch (IOException e) {
            logger.error("Failed to initialize Firebase Admin SDK", e);
            return null;
        }
    }
}
