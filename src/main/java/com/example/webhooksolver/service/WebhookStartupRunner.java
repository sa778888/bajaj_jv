package com.example.webhooksolver.service;

import com.example.webhooksolver.dto.GenerateWebhookRequest;
import com.example.webhooksolver.dto.GenerateWebhookResponse;
import com.example.webhooksolver.dto.TestWebhookRequest;
import com.example.webhooksolver.entity.Solution;
import com.example.webhooksolver.repo.SolutionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class WebhookStartupRunner implements ApplicationRunner {

    private final RestTemplate rest;
    private final Environment env;
    private final SolutionRepository repo;

    private static final String GENERATE_WEBHOOK_URL =
            "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";

    private static final String TEST_WEBHOOK_URL =
            "https://bfhldevapigw.healthrx.co.in/hiring/testWebhook/JAVA";

    public WebhookStartupRunner(RestTemplate rest, Environment env, SolutionRepository repo) {
        this.rest = rest;
        this.env = env;
        this.repo = repo;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {

        String name = env.getProperty("candidate.name");
        String regNo = env.getProperty("candidate.regNo");
        String email = env.getProperty("candidate.email");

        String finalQuery = env.getProperty("candidate.finalQuery");

        if (name == null || regNo == null || email == null) {
            System.err.println("Please set candidate.name, candidate.regNo, candidate.email in application.properties.");
            return;
        }

        System.out.println("Posting to generateWebhook...");
        GenerateWebhookRequest req = new GenerateWebhookRequest(name, regNo, email);

        ResponseEntity<GenerateWebhookResponse> resp;
        try {
            resp = rest.postForEntity(GENERATE_WEBHOOK_URL, req, GenerateWebhookResponse.class);
        } catch (Exception e) {
            System.err.println("generateWebhook request failed: " + e.getMessage());
            return;
        }

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            System.err.println("generateWebhook returned error: " + resp.getStatusCode());
            return;
        }

        GenerateWebhookResponse body = resp.getBody();
        String webhookUrl = body.getWebhook();
        String accessToken = body.getAccessToken();

        System.out.println("Received webhook: " + webhookUrl);
        System.out.println("AccessToken present: " + (accessToken == null ? "false" : "true"));

        // Load SQL FROM FILE if not in properties
        if (finalQuery == null || finalQuery.isBlank()) {
            Path file = Path.of("finalQuery.txt");
            if (Files.exists(file)) {
                finalQuery = Files.readString(file).trim();
                System.out.println("Loaded finalQuery from finalQuery.txt");
            }
        }

        // If still missing, abort
        if (finalQuery == null || finalQuery.isBlank()) {
            System.err.println("ERROR: No final SQL found. Put it in application.properties or finalQuery.txt");
            return;
        }

        // Save locally
        Solution s = new Solution(regNo, finalQuery);
        repo.save(s);
        System.out.println("Saved SQL locally with id: " + s.getId());

        // Prepare the submission request
        TestWebhookRequest submitReq = new TestWebhookRequest(finalQuery);

        // Try both Authorization formats
        String[] authHeaders = new String[]{
                "Bearer " + accessToken,
                accessToken
        };

        boolean submitted = false;

        for (String auth : authHeaders) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", auth);

            HttpEntity<TestWebhookRequest> entity = new HttpEntity<>(submitReq, headers);

            System.out.println("Trying Authorization header: " + (auth.startsWith("Bearer") ? "Bearer <token>" : "Raw token"));

            try {
                ResponseEntity<String> submitResp =
                        rest.postForEntity(TEST_WEBHOOK_URL, entity, String.class);

                System.out.println("Submission status: " + submitResp.getStatusCode());
                System.out.println("Response: " + submitResp.getBody());

                submitted = true;
                break;

            } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized unauth) {
                System.out.println("401 using this header. Trying next...");
            } catch (Exception ex) {
                System.err.println("Submission failed: " + ex.getMessage());
                break;
            }
        }

        if (!submitted) {
            System.err.println("All attempts failed. Token/Authorization format issue.");
        } else {
            System.out.println("Submission completed successfully.");
        }
    }
}
