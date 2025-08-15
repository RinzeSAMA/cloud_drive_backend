package com.cloudrive.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文件列表分页响应VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileListPageVO {
    /**
     * 文件列表
     */
    private List<FileListVO> files;

    /**
     * 总记录数
     */
    private long totalElements;

    /**
     * 总页数
     */
    private int totalPages;

    /**
     * 当前页码
     */
    private int currentPage;

    /**
     * 每页大小
     */
    private int pageSize;
} 