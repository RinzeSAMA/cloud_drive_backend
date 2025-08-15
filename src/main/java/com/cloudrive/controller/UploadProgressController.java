package com.cloudrive.controller;

import com.cloudrive.common.annotation.RateLimit;
import com.cloudrive.common.annotation.RateLimit.Dimension;
import com.cloudrive.common.result.Result;
import com.cloudrive.convert.UploadProgressConvertUtil;
import com.cloudrive.model.vo.UploadProgressVO;
import com.cloudrive.service.UploadProgressService;
import com.cloudrive.service.UploadProgressService.UploadTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 上传进度控制器
 */
@RestController
@RequestMapping("/api/upload-progress")
public class UploadProgressController {

    private final UploadProgressService uploadProgressService;
    private final UploadProgressConvertUtil uploadProgressConvertUtil;

    @Autowired
    public UploadProgressController(UploadProgressService uploadProgressService, UploadProgressConvertUtil uploadProgressConvertUtil, UploadProgressConvertUtil uploadProgressConvertUtil1) {
        this.uploadProgressService = uploadProgressService;
        this.uploadProgressConvertUtil = uploadProgressConvertUtil1;
    }

    /**
     * 获取上传进度
     * @param taskId 任务ID
     * @return 上传进度信息
     */
    @GetMapping("/{taskId}")
    @RateLimit(dimensions = { Dimension.USER, Dimension.IP }, permitsPerSecond = 10.0, timeout = 500)
    public Result<UploadProgressVO> getUploadProgress(@PathVariable String taskId) {
        UploadTask task = uploadProgressService.getUploadTask(taskId);
        if (task == null) {
            return Result.error("上传任务不存在");
        }
        
        UploadProgressVO progressVO = UploadProgressConvertUtil.toUploadProgressVO(task);
        
        return Result.success(progressVO);
    }
}
