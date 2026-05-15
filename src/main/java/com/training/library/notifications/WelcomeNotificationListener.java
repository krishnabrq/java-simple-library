package com.training.library.notifications;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class WelcomeNotificationListener {

  static final String INSTANCE = "welcomeNotifier";

  private static final Logger log = LoggerFactory.getLogger(WelcomeNotificationListener.class);

  private final WelcomeNotifierClient client;

  public WelcomeNotificationListener(WelcomeNotifierClient client) {
    this.client = client;
  }

  @Async
  @EventListener
  @CircuitBreaker(name = INSTANCE, fallbackMethod = "onFailure")
  @Retry(name = INSTANCE)
  public void onUserSignedUp(UserSignedUpEvent event) {
    log.info("welcome: calling notifier for userId={}", event.userId());
    client.notify(
        new WelcomeNotifierPayload(
            event.userId(), "Welcome, " + event.name(), "Thanks for joining the library!"));
    log.info("welcome: notifier ok for userId={}", event.userId());
  }

  @SuppressWarnings("unused")
  void onFailure(UserSignedUpEvent event, Throwable t) {
    log.warn(
        "welcome: notifier exhausted retries / circuit open for userId={} cause={}",
        event.userId(),
        t.toString());
  }
}
