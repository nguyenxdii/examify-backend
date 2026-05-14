package com.examify.examify.backend.service;

import com.examify.examify.backend.model.Question;
import com.examify.examify.backend.model.Submission;
import com.examify.examify.backend.model.SubmissionAnswer;
import com.examify.examify.backend.repository.SubmissionAnswerRepository;
import com.examify.examify.backend.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AsyncGradingService {

    private final GeminiService geminiService;
    private final SubmissionAnswerRepository submissionAnswerRepository;
    private final SubmissionRepository submissionRepository;

    @Async
    public void gradeSubmissionEssaysAsync(String submissionId, List<Question> questions) {
        List<SubmissionAnswer> allSaved = submissionAnswerRepository.findBySubmissionId(submissionId);
        Optional<Submission> submissionOpt = submissionRepository.findById(submissionId);
        
        if (submissionOpt.isEmpty()) return;
        Submission saved = submissionOpt.get();

        boolean anyError = false;
        for (SubmissionAnswer sa : allSaved) {
            final String qId = sa.getQuestionId();
            Optional<Question> qOpt = questions.stream().filter(q -> q.getId().equals(qId)).findFirst();
            
            if (qOpt.isPresent() && "essay".equals(qOpt.get().getType())) {
                Question q = qOpt.get();
                try {
                    Map<String, Object> aiResult = geminiService.evaluateEssay(
                            q.getContent(),
                            q.getSampleAnswer(),
                            q.getScoringCriteria(),
                            sa.getEssayAnswer());

                    Object scoreObj = aiResult.get("score");
                    float score = 0.0f;
                    if (scoreObj instanceof Double) score = ((Double) scoreObj).floatValue();
                    else if (scoreObj instanceof Float) score = (Float) scoreObj;
                    else if (scoreObj instanceof Integer) score = ((Integer) scoreObj).floatValue();
                    else if (scoreObj instanceof Number) score = ((Number) scoreObj).floatValue();

                    sa.setAiScore(score);
                    sa.setAiComment((String) aiResult.get("feedback"));
                    
                    // Also update actual grading fields
                    boolean isCorrect = score >= 5.0f;
                    sa.setCorrect(isCorrect);
                    float pointShare = questions.isEmpty() ? 0 : 10.0f / questions.size();
                    sa.setFinalScore(isCorrect ? pointShare : 0);
                    
                    submissionAnswerRepository.save(sa);
                } catch (Exception e) {
                    System.err.println("Async AI Grading error for submission " + submissionId + ": " + e.getMessage());
                    anyError = true;
                }
            }
        }
        
        // Finalize submission score
        List<SubmissionAnswer> updatedAnswers = submissionAnswerRepository.findBySubmissionId(submissionId);
        long totalCorrect = updatedAnswers.stream().filter(SubmissionAnswer::isCorrect).count();
        float totalScore = saved.getTotalQuestions() == 0 ? 0 : ((float) totalCorrect / saved.getTotalQuestions()) * 10;

        saved.setScore(totalScore);
        saved.setCorrectCount((int) totalCorrect);
        
        // If all essays are now graded (by AI), we mark it as pending review
        // to let the teacher confirm the final result.
        saved.setGradingStatus("pending_review");
        saved.setGraded(false); 
        
        submissionRepository.save(saved);
    }
}
