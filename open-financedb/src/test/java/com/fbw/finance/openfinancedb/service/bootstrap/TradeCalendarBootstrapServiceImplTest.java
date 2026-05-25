package com.fbw.finance.openfinancedb.service.bootstrap;

import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarPageReqVO;
import com.fbw.finance.openfinancedb.datasource.tushare.TushareReferenceDataSource;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.model.market.AdjFactorPoint;
import com.fbw.finance.openfinancedb.repository.data.TradeCalendarRepository;
import com.fbw.finance.openfinancedb.service.bootstrap.impl.TradeCalendarBootstrapServiceImpl;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TradeCalendarBootstrapServiceImplTest {

    @Test
    void initializeIfEmptyFetchesTradeCalendarThroughCurrentYearEnd() {
        RecordingTushareReferenceDataSource dataSource = new RecordingTushareReferenceDataSource();
        FakeTradeCalendarRepository repository = new FakeTradeCalendarRepository(0);
        LocalDate startDate = LocalDate.of(2015, 1, 1);

        new TradeCalendarBootstrapServiceImpl(dataSource, repository, startDate).initializeIfEmpty();

        LocalDate expectedEndDate = LocalDate.of(LocalDate.now().getYear(), 12, 31);
        assertThat(dataSource.requests)
                .containsExactly(
                        new FetchRequest("SSE", startDate, expectedEndDate),
                        new FetchRequest("SZSE", startDate, expectedEndDate));
    }

    private record FetchRequest(String exchange, LocalDate startDate, LocalDate endDate) {
    }

    private static class RecordingTushareReferenceDataSource implements TushareReferenceDataSource {
        private final List<FetchRequest> requests = new ArrayList<>();

        @Override
        public List<StockInfoEntity> fetchStockBasicList() {
            return List.of();
        }

        @Override
        public List<TradeCalendarEntity> fetchTradeCalendar(String exchange, LocalDate startDate, LocalDate endDate) {
            requests.add(new FetchRequest(exchange, startDate, endDate));
            return List.of();
        }

        @Override
        public List<AdjFactorPoint> fetchAdjFactors(String symbol, LocalDate startDate, LocalDate endDate) {
            return List.of();
        }
    }

    private static class FakeTradeCalendarRepository implements TradeCalendarRepository {
        private final long count;

        private FakeTradeCalendarRepository(long count) {
            this.count = count;
        }

        @Override
        public Long create(TradeCalendarEntity entity) {
            return entity.getId();
        }

        @Override
        public boolean update(TradeCalendarEntity entity) {
            return true;
        }

        @Override
        public boolean upsertByExchangeAndTradeDate(TradeCalendarEntity entity) {
            return true;
        }

        @Override
        public boolean deleteById(Long id) {
            return true;
        }

        @Override
        public Optional<TradeCalendarEntity> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<TradeCalendarEntity> findByExchangeAndTradeDate(String exchange, LocalDate tradeDate) {
            return Optional.empty();
        }

        @Override
        public long count() {
            return count;
        }

        @Override
        public List<TradeCalendarEntity> findOpenDays(String exchange, LocalDate startDate, LocalDate endDate) {
            return List.of();
        }

        @Override
        public PageResult<TradeCalendarEntity> page(TradeCalendarPageReqVO reqVO) {
            return new PageResult<>(List.of(), 0L);
        }
    }
}
