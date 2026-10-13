package com.example.mockinterview.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

@Service
public class GeminiService {

    private final ChatModel chatModel;

    public GeminiService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String generateQuestions(String jobPosition, String jobDesc, String jobExperience) {
        String prompt = "Generate 5 interview questions and answers for:\n" +
                "Job Position: " + jobPosition + "\n" +
                "Job Description: " + jobDesc + "\n" +
                "Years of Experience: " + jobExperience + "\n\n" +
                "Please provide a valid JSON array with this exact format:\n" +
                "[\n" +
                "  {\n" +
                "    \"Question\": \"Your interview question here?\",\n" +
                "    \"Answer\": \"Your detailed answer here.\"\n" +
                "  }\n" +
                "]\n\n" +
                "Keep questions professional and relevant to the job requirements.";

        return callModelAndCleanJson(prompt);
    }

    public String generateCompanyQuestions(String jobPosition, String jobDesc, String jobExperience,
            String typeQuestion, String company) {
        String prompt = "Given the following details:\n" +
                "- Job Position: " + jobPosition + "\n" +
                "- Job Description: " + jobDesc + "\n" +
                "- Years of Experience: " + jobExperience + "\n" +
                "- Type of Question: " + typeQuestion + "\n" +
                "- Previous Questions from this Company: " + company + "\n\n" +
                "Please generate 5 interview questions relevant to the job position, experience level, and question type provided. Each question should be accompanied by a comprehensive answer. The output should be in JSON format with \"Question\" and \"Answer\" as fields.\n\n"
                +
                "Example format:\n" +
                "[\n" +
                "  {\n" +
                "    \"Question\": \"Your question here\",\n" +
                "    \"Answer\": \"The corresponding answer here\"\n" +
                "  }\n" +
                "]";

        return callModelAndCleanJson(prompt);
    }

    public String evaluateAnswer(String question, String userAns) {
        String prompt = "Question: " + question + "\n" +
                "User Answer: " + userAns + "\n\n" +
                "Evaluate the user's answer for the given interview question.\n" +
                "Provide a rating out of 10 and feedback (3-5 lines) for improvement.\n" +
                "Respond in this exact JSON format:\n" +
                "{\n" +
                "  \"rating\": 7,\n" +
                "  \"feedback\": \"Your feedback here.\"\n" +
                "}";

        return callModelAndCleanJson(prompt);
    }

    public String transcribe(byte[] audioBytes, String contentType) {
        Resource audioResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "audio.webm";
            }
        };

        String mimeType = (contentType != null && !contentType.isEmpty()) ? contentType : "audio/webm";

        UserMessage userMessage = UserMessage.builder()
                .text("Transcribe the following audio accurately. Return only the transcribed text with no additional commentary.")
                .media(new Media(MimeTypeUtils.parseMimeType(mimeType), audioResource))
                .build();

        return chatModel.call(new Prompt(userMessage)).getResult().getOutput().getText().trim();
    }

    private String callModelAndCleanJson(String prompt) {
        String response = chatModel.call(prompt).trim();
        return cleanJson(response);
    }

    private String cleanJson(String rawResponse) {
        String cleaned = rawResponse
                .replaceAll("(?i)```json", "")
                .replaceAll("```", "")
                .trim();
        return cleaned;
    }
}
