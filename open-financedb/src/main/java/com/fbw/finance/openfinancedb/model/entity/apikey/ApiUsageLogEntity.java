package com.fbw.finance.openfinancedb.model.entity.apikey;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("api_usage_log")
public class ApiUsageLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long apiKeyId;
    private String method;
    private String path;
    private Integer statusCode;
    private Long latencyMs;
    private Boolean success;
    private LocalDateTime createdAt;
    @TableField(exist = false)
    private Long count;
}
