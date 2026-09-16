package com.iflytek.skillhub.projection;

import com.iflytek.skillhub.domain.social.SkillRatingRepository;
import com.iflytek.skillhub.domain.social.SkillStarRepository;
import com.iflytek.skillhub.domain.social.SkillSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillEngagementProjectionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private SkillStarRepository skillStarRepository;

    @Mock
    private SkillRatingRepository skillRatingRepository;

    @Mock
    private SkillSubscriptionRepository skillSubscriptionRepository;

    private SkillEngagementProjectionService service() {
        return new SkillEngagementProjectionService(
                jdbcTemplate,
                skillStarRepository,
                skillRatingRepository,
                skillSubscriptionRepository
        );
    }

    @Test
    void refreshStarCount_updates_denormalized_column() {
        SkillEngagementProjectionService service = service();
        when(skillStarRepository.countBySkillId(1L)).thenReturn(42L);

        service.refreshStarCount(1L);

        verify(jdbcTemplate).update("UPDATE skill SET star_count = ? WHERE id = ?", 42, 1L);
    }

    @Test
    void refreshRatingStats_updates_denormalized_columns() {
        SkillEngagementProjectionService service = service();
        when(skillRatingRepository.averageScoreBySkillId(1L)).thenReturn(4.2);
        when(skillRatingRepository.countBySkillId(1L)).thenReturn(10);

        service.refreshRatingStats(1L);

        verify(jdbcTemplate).update(
                "UPDATE skill SET rating_avg = ?, rating_count = ? WHERE id = ?",
                4.2,
                10,
                1L
        );
    }

    @Test
    void refreshSubscriptionCount_updates_denormalized_column() {
        SkillEngagementProjectionService service = service();
        when(skillSubscriptionRepository.countBySkillId(1L)).thenReturn(7L);

        service.refreshSubscriptionCount(1L);

        verify(jdbcTemplate).update("UPDATE skill SET subscription_count = ? WHERE id = ?", 7, 1L);
    }
}
