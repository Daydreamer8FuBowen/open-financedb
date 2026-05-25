package com.fbw.finance.openfinancedb.model.entity.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据同步日志实体，对应 `sync_log` 表。
 */
@Data
@TableName("sync_log")
public class SyncLogEntity {

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 业务日志标识。 */
    private String logId;
    /** 任务标识。 */
    private String taskId;
    /** 股票代码。 */
    private String symbol;
    /** 同步数据类型。 */
    private String dataType;
    /** 数据来源。 */
    private String dataSource;
    /** 执行开始时间。 */
    private LocalDateTime startTime;
    /** 执行结束时间。 */
    private LocalDateTime endTime;
    /** 拉取耗时，单位毫秒。 */
    private Long fetchLatencyMs;
    /** 清洗耗时，单位毫秒。 */
    private Long cleanLatencyMs;
    /** 写入耗时，单位毫秒。 */
    private Long writeLatencyMs;
    /** 总耗时，单位毫秒。 */
    private Long totalLatencyMs;
    /** 拉取记录数。 */
    private Integer fetchedCount;
    /** 清洗记录数。 */
    private Integer cleanedCount;
    /** 写入记录数。 */
    private Integer writtenCount;
    /** 是否执行成功。 */
    private Boolean success;
    /** 错误类型。 */
    private String errorType;
    /** 错误详情。 */
    private String errorMessage;
    /** 创建时间。 */
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
