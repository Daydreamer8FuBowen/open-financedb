package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineCompleteness;
import com.fbw.finance.openfinancedb.model.market.KlinePeriod;
import com.fbw.finance.openfinancedb.model.market.KlineQuery;
import com.fbw.finance.openfinancedb.repository.market.KlineRepository;
import com.fbw.finance.openfinancedb.service.market.KlineAggregationService;
import com.fbw.finance.openfinancedb.service.market.KlineCompletionService;
import com.fbw.finance.openfinancedb.service.market.KlineQueryService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KlineQueryServiceImpl implements KlineQueryService {

    private final KlineRepository klineRepository;
    private final KlineAggregationService aggregationService;
    private final KlineCompletionService completionService;

    public KlineQueryServiceImpl(
            KlineRepository klineRepository,
            KlineAggregationService aggregationService,
            KlineCompletionService completionService) {
        this.klineRepository = klineRepository;
        this.aggregationService = aggregationService;
        this.completionService = completionService;
    }

    @Override
    public List<KlineBar> query(KlineQuery query) {
        // Read priority: target period first, then derive from 1m, then trigger 1m completion.
        List<KlineBar> periodBars = klineRepository.query(query.symbol(), query.period(), query.startTime(), query.endTime());
        KlineCompleteness periodCompleteness = klineRepository.checkCompleteness(
                query.symbol(), query.period(), query.startTime(), query.endTime());
        if (periodCompleteness.complete()) {
            return periodBars;
        }

        if (query.period() == KlinePeriod.MINUTE_1) {
            // A 1m miss cannot be aggregated from a smaller period, so completion goes directly
            // to the datasource-backed path.
            completionService.completeMinuteData(query);
            return klineRepository.query(query.symbol(), KlinePeriod.MINUTE_1, query.startTime(), query.endTime());
        }

        KlineCompleteness minuteCompleteness = klineRepository.checkCompleteness(
                query.symbol(), KlinePeriod.MINUTE_1, query.startTime(), query.endTime());
        if (!minuteCompleteness.complete()) {
            completionService.completeMinuteData(new KlineQuery(
                    query.symbol(), KlinePeriod.MINUTE_1, query.startTime(), query.endTime()));
        }

        List<KlineBar> minuteBars = klineRepository.query(query.symbol(), KlinePeriod.MINUTE_1, query.startTime(), query.endTime());
        List<KlineBar> aggregated = aggregationService.aggregate(minuteBars, query.period());
        // Cache the derived period back into Influx so the next query can hit the target period path.
        klineRepository.upsert(aggregated);
        return aggregated;
    }
}
