package com.examify.examify.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class AdminUserDetailDTO {
    private String id;
    private String email;
    private String fullName;
    private String role;
    private String school;
    private String field;
    private boolean locked;
    private long totalExams;
    private long totalRooms;
    private long totalSubmissions; 
    
    private UserMetrics metrics;
    private Instant createdAt;

    @Data
    @Builder
    public static class UserMetrics {
        private MetricDetail apiRequests;
        private MetricDetail storage;
        private MetricDetail aiTokens;
    }

    @Data
    @Builder
    public static class MetricDetail {
        private long day1;
        private long day7;
        private long day30;
    }
}
