package com.training.library.stats;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STAFF')")
public class StatsController {

  private final LibraryStatsRepository statsRepository;

  @GetMapping
  public Map<String, Object> snapshot(
      @RequestParam(name = "top", defaultValue = "5") @Min(1) @Max(50) Integer top) {
    return statsRepository.snapshot(top);
  }
}
