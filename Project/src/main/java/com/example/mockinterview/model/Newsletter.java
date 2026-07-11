package com.example.mockinterview.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "newsletter")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Newsletter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "new_name")
    private String newName;

    @Column(name = "new_email")
    private String newEmail;

    @Column(name = "new_message", columnDefinition = "TEXT")
    private String newMessage;

    @Column(name = "created_at")
    private String createdAt;
}
