package com.example.mockinterview.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FirebaseClientConfigController {

    @Value("${firebase.client.apiKey}")
    private String apiKey;

    @Value("${firebase.client.authDomain}")
    private String authDomain;

    @Value("${firebase.client.projectId}")
    private String projectId;

    @Value("${firebase.client.storageBucket}")
    private String storageBucket;

    @Value("${firebase.client.messagingSenderId}")
    private String messagingSenderId;

    @Value("${firebase.client.appId}")
    private String appId;

    @Value("${firebase.client.measurementId}")
    private String measurementId;

    @GetMapping(value = "/api/config/firebase.js", produces = "application/javascript")
    public ResponseEntity<String> getFirebaseConfigJs() {
        String jsContent = String.format(
            "const firebaseConfig = {\n" +
            "  apiKey: \"%s\",\n" +
            "  authDomain: \"%s\",\n" +
            "  projectId: \"%s\",\n" +
            "  storageBucket: \"%s\",\n" +
            "  messagingSenderId: \"%s\",\n" +
            "  appId: \"%s\",\n" +
            "  measurementId: \"%s\"\n" +
            "};\n" +
            "if (typeof firebase !== 'undefined' && !firebase.apps.length) {\n" +
            "    firebase.initializeApp(firebaseConfig);\n" +
            "}",
            apiKey, authDomain, projectId, storageBucket, messagingSenderId, appId, measurementId
        );
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/javascript")
            .body(jsContent);
    }
}
