package com.cloudrive.controller;

import com.cloudrive.common.annotation.RateLimit;
import com.cloudrive.common.enums.FileStatus;
import com.cloudrive.common.result.Result;
import com.cloudrive.common.util.UserContext;
import com.cloudrive.model.dto.FileQueryDTO;
import com.cloudrive.model.vo.FileListPageVO;
import com.cloudrive.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 回收站管理
 */
@RestController
@RequestMapping("/api/recycle-bin")
@RequiredArgsConstructor
public class RecycleController {

    private final FileService fileService;
    /**
     * 加载回收站文件列表
     */
    @GetMapping
    @RateLimit(dimensions = { RateLimit.Dimension.USER }, permitsPerSecond = 10.0, timeout = 500)
    public Result<FileListPageVO> recycleListFiles(FileQueryDTO queryDTO) {
        queryDTO.setUserId(UserContext.getCurrentUserId());
        queryDTO.setSortBy("deleteTime");
        FileListPageVO pageVO = fileService.getRecycleListFiles(queryDTO);
        return Result.success(pageVO);
    }

    /**
     * 彻底删除（逻辑删除->物理删除）
     */
    @PostMapping ("/delFile")
    @RateLimit(dimensions = { RateLimit.Dimension.USER }, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> deleteFile(@RequestBody List<Long> fileIds ) {
        fileService.deleteFileForPermanent(fileIds);
        return Result.success();
    }


}
