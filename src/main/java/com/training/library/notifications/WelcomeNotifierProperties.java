package com.training.library.notifications;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.welcome-notifier")
public record WelcomeNotifierProperties(@NotBlank String baseUrl) {}
