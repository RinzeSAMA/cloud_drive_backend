package com.cloudrive.model.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class FileQueryDTO {

    private Long userId;
    /**
     * 父文件夹ID
     */
    private Long parentId;

    /**
     * 页码（从0开始）
     */
    @Min(value = 0, message = "页码不能小于0")
    private Integer pageNo = 1;

    /**
     * 每页大小
     */
    @Min(value = 1, message = "每页大小不能小于1")
    private Integer pageSize = 10;

    /**
     * 排序字段
     */
    private String sortBy = "createdAt";

    /**
     * 排序方向（asc/desc）
     */
    private String sortDirection = "desc";

}
