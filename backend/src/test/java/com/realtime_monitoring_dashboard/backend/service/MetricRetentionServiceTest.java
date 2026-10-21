package com.realtime_monitoring_dashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;

import com.realtime_monitoring_dashboard.backend.repository.MetricRepository;

class MetricRetentionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);

    private final MetricRepository metricRepository = mock(MetricRepository.class);
    private final MetricRetentionService service = new MetricRetentionService(metricRepository, 7);

    private static List<Long> ids(long from, int count) {
        return LongStream.range(from, from + count).boxed().toList();
    }

    @Test
    void usesCutoffRetentionDaysBeforeNow() {
        when(metricRepository.findIdsOlderThan(any(), any())).thenReturn(List.of());

        service.deleteMetricsOlderThanRetention(NOW);

        verify(metricRepository).findIdsOlderThan(eq(NOW.minusDays(7)), any(Pageable.class));
    }

    @Test
    void deletesNothingWhenNoMetricIsOldEnough() {
        when(metricRepository.findIdsOlderThan(any(), any())).thenReturn(List.of());

        assertThat(service.deleteMetricsOlderThanRetention(NOW)).isZero();

        verify(metricRepository, never()).deleteByIdIn(anyList());
    }

    @Test
    void keepsDeletingFullBatchesUntilALastSmallerBatch() {
        int batch = MetricRetentionService.BATCH_SIZE;
        List<Long> first = ids(1, batch);
        List<Long> second = ids(1L + batch, 3);
        when(metricRepository.findIdsOlderThan(any(), any())).thenReturn(first, second);
        when(metricRepository.deleteByIdIn(first)).thenReturn(batch);
        when(metricRepository.deleteByIdIn(second)).thenReturn(3);

        assertThat(service.deleteMetricsOlderThanRetention(NOW)).isEqualTo(batch + 3);

        verify(metricRepository, times(2)).findIdsOlderThan(any(), any());
        verify(metricRepository, times(2)).deleteByIdIn(anyList());
    }

    @Test
    void asksForOneFullBatchAtATime() {
        when(metricRepository.findIdsOlderThan(any(), any())).thenReturn(List.of());

        service.deleteMetricsOlderThanRetention(NOW);

        verify(metricRepository).findIdsOlderThan(any(), eq(Pageable.ofSize(MetricRetentionService.BATCH_SIZE)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -30})
    void refusesToStartWithRetentionBelowOneDay(int days) {
        assertThatThrownBy(() -> new MetricRetentionService(metricRepository, days))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("METRIC_RETENTION_DAYS");
    }
}
