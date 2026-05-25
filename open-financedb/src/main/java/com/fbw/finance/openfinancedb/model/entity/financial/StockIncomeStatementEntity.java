package com.fbw.finance.openfinancedb.model.entity.financial;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Stock income statement by report period.
 */
@Data
@TableName("stock_income_statement")
public class StockIncomeStatementEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String symbol;
    private LocalDate annDate;
    private LocalDate fAnnDate;
    private LocalDate endDate;
    private String reportType;
    private String compType;
    private String endType;
    private BigDecimal basicEps;
    private BigDecimal dilutedEps;
    private BigDecimal totalRevenue;
    private BigDecimal revenue;
    private BigDecimal intIncome;
    private BigDecimal commIncome;
    private BigDecimal nCommisIncome;
    private BigDecimal nOthIncome;
    private BigDecimal nOthBIncome;
    private BigDecimal othBIncome;
    private BigDecimal fvValueChgGain;
    private BigDecimal investIncome;
    private BigDecimal forexGain;
    private BigDecimal totalCogs;
    private BigDecimal intExp;
    private BigDecimal commExp;
    private BigDecimal bizTaxSurchg;
    private BigDecimal adminExp;
    private BigDecimal operExp;
    private BigDecimal operateProfit;
    private BigDecimal nonOperIncome;
    private BigDecimal nonOperExp;
    private BigDecimal totalProfit;
    private BigDecimal incomeTax;
    @TableField("n_income")
    private BigDecimal netIncome;
    private BigDecimal nIncomeAttrP;
    private BigDecimal othComprIncome;
    private BigDecimal tComprIncome;
    private BigDecimal comprIncAttrP;
    private BigDecimal continuedNetProfit;
    private String updateFlag;
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
