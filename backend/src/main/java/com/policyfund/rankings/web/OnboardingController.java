package com.policyfund.rankings.web;

import com.policyfund.rankings.dto.OnboardingItem;
import com.policyfund.rankings.service.OnboardingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    private final OnboardingService service;

    public OnboardingController(OnboardingService service) {
        this.service = service;
    }

    @GetMapping
    public List<OnboardingItem> onboarding(
            @RequestParam(required = false, defaultValue = "최근 30일") String period) {
        return service.onboarding(period);
    }
}
