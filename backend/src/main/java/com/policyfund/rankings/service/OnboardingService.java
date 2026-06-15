package com.policyfund.rankings.service;

import com.policyfund.rankings.dto.OnboardingItem;
import com.policyfund.rankings.dto.RankingItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** UC-5: UC-4 랭킹을 그대로 학습 우선순위로 환산한다(별도 추천 로직 없음). */
@Service
public class OnboardingService {

    private final RankingService rankingService;

    public OnboardingService(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @Transactional
    public List<OnboardingItem> onboarding(String period) {
        List<RankingItem> rankings = rankingService.rankings(period);
        List<OnboardingItem> items = new ArrayList<>();
        int order = 1;
        for (RankingItem r : rankings) {
            String reason = "실무자 검색 " + r.searchCount() + "회·조회 " + r.viewCount()
                    + "회로 우선순위가 높습니다.";
            items.add(new OnboardingItem(order++, r.category(), reason,
                    r.searchCount(), r.viewCount(), r.relatedArticles()));
        }
        return items;
    }
}
