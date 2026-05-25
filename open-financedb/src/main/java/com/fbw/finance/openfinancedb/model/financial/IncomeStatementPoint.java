package com.fbw.finance.openfinancedb.model.financial;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IncomeStatementPoint(
        /** 股票代码，例如 000001.SZ。 */
        String symbol,
        /** 公告日期。 */
        LocalDate annDate,
        /** 实际公告日期。 */
        LocalDate fAnnDate,
        /** 报告期截止日期。 */
        LocalDate endDate,
        /** 报告类型，取值含义以 Tushare 文档为准。 */
        String reportType,
        /** 公司类型，1 为一般工商业，2 为银行，3 为保险，4 为证券。 */
        String compType,
        /** 报告期类型。 */
        String endType,
        /** 基本每股收益。 */
        BigDecimal basicEps,
        /** 稀释每股收益。 */
        BigDecimal dilutedEps,
        /** 营业总收入。 */
        BigDecimal totalRevenue,
        /** 营业收入。 */
        BigDecimal revenue,
        /** 利息收入，金融类公司常用。 */
        BigDecimal intIncome,
        /** 手续费及佣金收入，金融类公司常用。 */
        BigDecimal commIncome,
        /** 手续费及佣金净收入。 */
        BigDecimal nCommisIncome,
        /** 其他经营净收益。 */
        BigDecimal nOthIncome,
        /** 加：其他业务净收益。 */
        BigDecimal nOthBIncome,
        /** 其他业务收入。 */
        BigDecimal othBIncome,
        /** 公允价值变动净收益。 */
        BigDecimal fvValueChgGain,
        /** 投资净收益。 */
        BigDecimal investIncome,
        /** 汇兑净收益。 */
        BigDecimal forexGain,
        /** 营业总成本。 */
        BigDecimal totalCogs,
        /** 利息支出，金融类公司常用。 */
        BigDecimal intExp,
        /** 手续费及佣金支出。 */
        BigDecimal commExp,
        /** 营业税金及附加。 */
        BigDecimal bizTaxSurchg,
        /** 管理费用。 */
        BigDecimal adminExp,
        /** 营业支出。 */
        BigDecimal operExp,
        /** 营业利润。 */
        BigDecimal operateProfit,
        /** 营业外收入。 */
        BigDecimal nonOperIncome,
        /** 营业外支出。 */
        BigDecimal nonOperExp,
        /** 利润总额。 */
        BigDecimal totalProfit,
        /** 所得税费用。 */
        BigDecimal incomeTax,
        /** 净利润。 */
        BigDecimal netIncome,
        /** 归属于母公司所有者的净利润。 */
        BigDecimal nIncomeAttrP,
        /** 其他综合收益。 */
        BigDecimal othComprIncome,
        /** 综合收益总额。 */
        BigDecimal tComprIncome,
        /** 归属于母公司所有者的综合收益总额。 */
        BigDecimal comprIncAttrP,
        /** 持续经营净利润。 */
        BigDecimal continuedNetProfit,
        /** 更新标识。 */
        String updateFlag
) {
}
