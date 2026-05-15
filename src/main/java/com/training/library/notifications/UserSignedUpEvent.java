package com.training.library.notifications;

public record UserSignedUpEvent(Long userId, String name, String email) {}
