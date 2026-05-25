package com.fbw.finance.openfinancedb.datasource.tushare;

import com.fbw.finance.openfinancedb.framework.http.HttpPriority;
import com.fbw.finance.openfinancedb.model.financial.IncomeStatementPoint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TushareFinancialDataSourceImpl implements TushareFinancialDataSource {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String INCOME_FIELDS = "ts_code,ann_date,f_ann_date,end_date,report_type,comp_type,end_type,"
            + "basic_eps,diluted_eps,total_revenue,revenue,int_income,comm_income,n_commis_income,n_oth_income,"
            + "n_oth_b_income,oth_b_income,fv_value_chg_gain,invest_income,forex_gain,total_cogs,int_exp,comm_exp,"
            + "biz_tax_surchg,admin_exp,oper_exp,operate_profit,non_oper_income,non_oper_exp,total_profit,"
            + "income_tax,n_income,n_income_attr_p,oth_compr_income,t_compr_income,compr_inc_attr_p,"
            + "continued_net_profit,update_flag";

    private final TushareClient tushareClient;

    public TushareFinancialDataSourceImpl(TushareClient tushareClient) {
        this.tushareClient = tushareClient;
    }

    @Override
    public List<IncomeStatementPoint> fetchIncome(String symbol, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ts_code", symbol);
        params.put("start_date", formatDate(startDate));
        params.put("end_date", formatDate(endDate));

        TushareResponse response = tushareClient.callAsync(new TushareRequest(
                TushareApi.INCOME.apiName(),
                params,
                INCOME_FIELDS,
                HttpPriority.NORMAL
        )).join();
        return toIncomeStatements(response);
    }

    private List<IncomeStatementPoint> toIncomeStatements(TushareResponse response) {
        List<IncomeStatementPoint> result = new ArrayList<>();
        FieldRows rows = rows(response);
        for (List<Object> item : rows.items()) {
            result.add(new IncomeStatementPoint(
                    rows.string(item, "ts_code"),
                    parseDate(rows.string(item, "ann_date")),
                    parseDate(rows.string(item, "f_ann_date")),
                    parseDate(rows.string(item, "end_date")),
                    rows.string(item, "report_type"),
                    rows.string(item, "comp_type"),
                    rows.string(item, "end_type"),
                    rows.decimal(item, "basic_eps"),
                    rows.decimal(item, "diluted_eps"),
                    rows.decimal(item, "total_revenue"),
                    rows.decimal(item, "revenue"),
                    rows.decimal(item, "int_income"),
                    rows.decimal(item, "comm_income"),
                    rows.decimal(item, "n_commis_income"),
                    rows.decimal(item, "n_oth_income"),
                    rows.decimal(item, "n_oth_b_income"),
                    rows.decimal(item, "oth_b_income"),
                    rows.decimal(item, "fv_value_chg_gain"),
                    rows.decimal(item, "invest_income"),
                    rows.decimal(item, "forex_gain"),
                    rows.decimal(item, "total_cogs"),
                    rows.decimal(item, "int_exp"),
                    rows.decimal(item, "comm_exp"),
                    rows.decimal(item, "biz_tax_surchg"),
                    rows.decimal(item, "admin_exp"),
                    rows.decimal(item, "oper_exp"),
                    rows.decimal(item, "operate_profit"),
                    rows.decimal(item, "non_oper_income"),
                    rows.decimal(item, "non_oper_exp"),
                    rows.decimal(item, "total_profit"),
                    rows.decimal(item, "income_tax"),
                    rows.decimal(item, "n_income"),
                    rows.decimal(item, "n_income_attr_p"),
                    rows.decimal(item, "oth_compr_income"),
                    rows.decimal(item, "t_compr_income"),
                    rows.decimal(item, "compr_inc_attr_p"),
                    rows.decimal(item, "continued_net_profit"),
                    rows.string(item, "update_flag")
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

    private record FieldRows(Map<String, Integer> fieldIndex, List<List<Object>> items) {

        private String string(List<Object> item, String field) {
            Object value = value(item, field);
            return value == null ? "" : String.valueOf(value);
        }

        private BigDecimal decimal(List<Object> item, String field) {
            Object value = value(item, field);
            if (value == null || String.valueOf(value).isBlank()) {
                return null;
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
