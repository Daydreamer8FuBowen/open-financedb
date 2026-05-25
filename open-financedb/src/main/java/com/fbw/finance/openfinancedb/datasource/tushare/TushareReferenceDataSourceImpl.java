package com.fbw.finance.openfinancedb.datasource.tushare;

import com.fbw.finance.openfinancedb.framework.http.HttpPriority;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.model.market.AdjFactorPoint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TushareReferenceDataSourceImpl implements TushareReferenceDataSource {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String STOCK_BASIC_FIELDS = "ts_code,symbol,name,area,industry,market,exchange,list_date,delist_date,list_status";
    private static final String TRADE_CAL_FIELDS = "exchange,cal_date,is_open,pretrade_date";
    private static final String ADJ_FACTOR_FIELDS = "ts_code,trade_date,adj_factor";

    private final TushareClient tushareClient;

    public TushareReferenceDataSourceImpl(TushareClient tushareClient) {
        this.tushareClient = tushareClient;
    }

    @Override
    public List<StockInfoEntity> fetchStockBasicList() {
        TushareResponse response = tushareClient.callAsync(new TushareRequest(
                TushareApi.STOCK_BASIC.apiName(),
                Map.of("list_status", "L"),
                STOCK_BASIC_FIELDS,
                HttpPriority.NORMAL
        )).join();
        return toStockInfo(response);
    }

    @Override
    public List<TradeCalendarEntity> fetchTradeCalendar(String exchange, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("exchange", exchange);
        params.put("start_date", formatDate(startDate));
        params.put("end_date", formatDate(endDate));

        TushareResponse response = tushareClient.callAsync(new TushareRequest(
                TushareApi.TRADE_CAL.apiName(),
                params,
                TRADE_CAL_FIELDS,
                HttpPriority.NORMAL
        )).join();
        return toTradeCalendar(response);
    }

    @Override
    public List<AdjFactorPoint> fetchAdjFactors(String symbol, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ts_code", symbol);
        params.put("start_date", formatDate(startDate));
        params.put("end_date", formatDate(endDate));

        TushareResponse response = tushareClient.callAsync(new TushareRequest(
                TushareApi.ADJ_FACTOR.apiName(),
                params,
                ADJ_FACTOR_FIELDS,
                HttpPriority.NORMAL
        )).join();
        return toAdjFactors(response);
    }

    private List<StockInfoEntity> toStockInfo(TushareResponse response) {
        List<StockInfoEntity> result = new ArrayList<>();
        FieldRows rows = rows(response);
        for (List<Object> item : rows.items()) {
            StockInfoEntity entity = new StockInfoEntity();
            entity.setSymbol(rows.string(item, "ts_code"));
            entity.setRawSymbol(rows.string(item, "symbol"));
            entity.setName(rows.string(item, "name"));
            entity.setArea(rows.string(item, "area"));
            entity.setIndustry(rows.string(item, "industry"));
            entity.setMarket(rows.string(item, "market"));
            entity.setExchange(rows.string(item, "exchange"));
            entity.setListDate(parseDate(rows.string(item, "list_date")));
            entity.setDelistDate(parseDate(rows.string(item, "delist_date")));
            entity.setStatus(toStockStatus(rows.string(item, "list_status")));
            entity.setDataSource("tushare");
            result.add(entity);
        }
        return result;
    }

    private List<TradeCalendarEntity> toTradeCalendar(TushareResponse response) {
        List<TradeCalendarEntity> result = new ArrayList<>();
        FieldRows rows = rows(response);
        for (List<Object> item : rows.items()) {
            TradeCalendarEntity entity = new TradeCalendarEntity();
            entity.setExchange(rows.string(item, "exchange"));
            entity.setTradeDate(parseDate(rows.string(item, "cal_date")));
            entity.setIsOpen("1".equals(rows.string(item, "is_open")));
            entity.setPreTradeDate(parseDate(rows.string(item, "pretrade_date")));
            result.add(entity);
        }
        return result;
    }

    private List<AdjFactorPoint> toAdjFactors(TushareResponse response) {
        List<AdjFactorPoint> result = new ArrayList<>();
        FieldRows rows = rows(response);
        for (List<Object> item : rows.items()) {
            result.add(new AdjFactorPoint(
                    rows.string(item, "ts_code"),
                    parseDate(rows.string(item, "trade_date")),
                    rows.decimal(item, "adj_factor"),
                    "tushare"
            ));
        }
        return result;
    }

    private FieldRows rows(TushareResponse response) {
        if (response.data() == null || response.data().fields() == null || response.data().items() == null) {
            return new FieldRows(Map.of(), List.of());
        }
        Map<String, Integer> fieldIndex = new LinkedHashMap<>();
        List<String> fields = response.data().fields();
        for (int i = 0; i < fields.size(); i++) {
            fieldIndex.put(fields.get(i), i);
        }
        return new FieldRows(fieldIndex, response.data().items());
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value, DATE_FORMATTER);
    }

    private String formatDate(LocalDate value) {
        return value.format(DATE_FORMATTER);
    }

    private String toStockStatus(String listStatus) {
        return "L".equalsIgnoreCase(listStatus) ? "LISTED" : listStatus;
    }

    private record FieldRows(Map<String, Integer> fieldIndex, List<List<Object>> items) {

        private String string(List<Object> item, String field) {
            Object value = value(item, field);
            return value == null ? "" : String.valueOf(value);
        }

        private BigDecimal decimal(List<Object> item, String field) {
            Object value = value(item, field);
            if (value == null || String.valueOf(value).isBlank()) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(String.valueOf(value));
        }

        private Object value(List<Object> item, String field) {
            Integer index = fieldIndex.get(field);
            if (index == null || index >= item.size()) {
                return null;
            }
            return item.get(index);
        }
    }
}
