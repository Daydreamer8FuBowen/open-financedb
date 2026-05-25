package com.fbw.finance.openfinancedb.datasource.tushare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fbw.finance.openfinancedb.framework.http.FinanceHttpClient;
import com.fbw.finance.openfinancedb.framework.http.FinanceHttpExecutor;
import com.fbw.finance.openfinancedb.model.financial.IncomeStatementPoint;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class TushareFinancialDataSourceTest {

    @Test
    void shouldFetchIncomeStatementsThroughTushareIncomeApi() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json("""
                    {"code":0,"msg":"","data":{"fields":["ts_code","ann_date","f_ann_date","end_date","report_type","comp_type","end_type","basic_eps","diluted_eps","total_revenue","revenue","int_income","comm_income","n_commis_income","n_oth_income","n_oth_b_income","oth_b_income","fv_value_chg_gain","invest_income","forex_gain","total_cogs","int_exp","comm_exp","biz_tax_surchg","admin_exp","oper_exp","operate_profit","non_oper_income","non_oper_exp","total_profit","income_tax","n_income","n_income_attr_p","oth_compr_income","t_compr_income","compr_inc_attr_p","continued_net_profit","update_flag"],"items":[["000001.SZ","20240315","20240316","20231231","1","1","4",1007.12,1008.12,1009.12,1010.12,1011.12,1013.12,1014.12,1015.12,1016.12,1024.12,1025.12,1026.12,1028.12,1029.12,1031.12,1032.12,1033.12,1035.12,1043.12,1048.12,1049.12,1050.12,1052.12,1053.12,1054.12,1055.12,1057.12,1058.12,1059.12,1091.12,"1"]]}}
                    """));
            server.start();
            FinanceHttpExecutor executor = new FinanceHttpExecutor(1, 1, 10);
            TushareFinancialDataSource dataSource = dataSource(server, executor);

            List<IncomeStatementPoint> incomes = dataSource.fetchIncome(
                    "000001.SZ",
                    LocalDate.of(2023, 1, 1),
                    LocalDate.of(2023, 12, 31)
            );

            assertFalse(incomes.isEmpty());
            IncomeStatementPoint income = incomes.getFirst();
            assertEquals("000001.SZ", income.symbol());
            assertEquals(LocalDate.of(2024, 3, 15), income.annDate());
            assertEquals(LocalDate.of(2024, 3, 16), income.fAnnDate());
            assertEquals(LocalDate.of(2023, 12, 31), income.endDate());
            assertEquals("1", income.reportType());
            assertEquals("1", income.compType());
            assertEquals("4", income.endType());
            assertEquals("1007.12", income.basicEps().toPlainString());
            assertEquals("1009.12", income.totalRevenue().toPlainString());
            assertEquals("1010.12", income.revenue().toPlainString());
            assertEquals("1054.12", income.netIncome().toPlainString());
            assertEquals("1091.12", income.continuedNetProfit().toPlainString());
            assertEquals("1", income.updateFlag());

            RecordedRequest request = server.takeRequest();
            String body = request.getBody().readUtf8();
            assertTrue(body.contains("\"api_name\":\"income\""));
            assertTrue(body.contains("\"ts_code\":\"000001.SZ\""));
            assertTrue(body.contains("\"start_date\":\"20230101\""));
            assertTrue(body.contains("\"end_date\":\"20231231\""));
            assertTrue(body.contains("\"fields\":\"ts_code,ann_date,f_ann_date,end_date,report_type,comp_type,end_type,basic_eps"));
            assertTrue(body.contains("continued_net_profit,update_flag\""));

            executor.close(Duration.ofSeconds(1));
        }
    }

    private static TushareFinancialDataSource dataSource(MockWebServer server, FinanceHttpExecutor executor) {
        TushareClient client = new TushareClient(
                server.url("/").toString(),
                "test-token",
                new FinanceHttpClient(new OkHttpClient(), executor),
                new TushareRateLimiter(Map.of("income", 10))
        );
        return new TushareFinancialDataSourceImpl(client);
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
