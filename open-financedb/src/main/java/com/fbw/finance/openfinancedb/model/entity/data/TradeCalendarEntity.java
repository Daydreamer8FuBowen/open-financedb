package com.fbw.finance.openfinancedb.model.entity.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 交易日历实体，对应 `trade_calendar` 表。
 */
@Data
@TableName("trade_calendar")
public class TradeCalendarEntity {

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 交易所代码。 */
    private String exchange;
    /** 交易日期。 */
    private LocalDate tradeDate;
    /** 是否开市。 */
    @TableField("is_open")
    private Boolean isOpen;
    /** 上一个交易日。 */
    private LocalDate preTradeDate;
    /** 下一个交易日。 */
    private LocalDate nextTradeDate;
    /** 创建时间。 */
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
