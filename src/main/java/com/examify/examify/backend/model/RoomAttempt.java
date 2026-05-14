package com.examify.examify.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "room_attempts")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomAttempt {
    @Id
    private String id;
    private String roomId;
    private String studentId;
    private String studentName;
    private Instant startTime;
    private Instant endTime;
    private int attemptNumber;
}
