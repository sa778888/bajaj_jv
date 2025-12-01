package com.example.webhooksolver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GenerateWebhookResponse {
    private String webhook;
    private String accessToken;

    @JsonProperty("webhook")
    public String getWebhook() { return webhook; }
    public void setWebhook(String webhook) { this.webhook = webhook; }

    @JsonProperty("accessToken")
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
}
