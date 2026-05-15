package com.training.library.notifications;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "welcomeNotifier", url = "${app.welcome-notifier.base-url}")
public interface WelcomeNotifierClient {

  @PostMapping("/posts")
  void notify(@RequestBody WelcomeNotifierPayload payload);
}
