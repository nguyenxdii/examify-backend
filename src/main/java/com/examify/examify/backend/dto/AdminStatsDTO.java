package com.examify.examify.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminStatsDTO {
    private StatCard users;
    private StatCard exams;
    private StatCard submissions;
    private StatCard rooms;

    @Data
    @Builder
    public static class StatCard {
        private long total;
        private double percentageChange; // vs last month
        private String trend; // "up" | "down" | "neutral"
    }
}
