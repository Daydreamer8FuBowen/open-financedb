package com.fbw.finance.openfinancedb.model.entity.data;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("stock_kline_missing_record")
public class StockKlineMissingRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String symbol;
    private String dataType;
    private String dataSource;
    private LocalDate missingDate;
    private String status;
    private LocalDateTime detectedAt;
    private LocalDateTime repairedAt;
    private String remark;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
