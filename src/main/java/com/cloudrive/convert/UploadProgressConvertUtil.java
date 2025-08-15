package com.cloudrive.convert;

import com.cloudrive.model.vo.UploadProgressVO;
import com.cloudrive.service.UploadProgressService.UploadTask;
import org.springframework.stereotype.Component;

@Component
public class UploadProgressConvertUtil {
    public static UploadProgressVO toUploadProgressVO(UploadTask task) {
        if (task == null) return null;
        UploadProgressVO vo = new UploadProgressVO();
        vo.setTaskId(task.getId());
        vo.setFilename(task.getFilename());
        vo.setTotalSize(task.getTotalSize());
        vo.setBytesTransferred(task.getBytesTransferred());
        vo.setProgress(task.getProgress());
        vo.setCompleted(task.isCompleted());
        vo.setSuccess(task.isSuccess());
        vo.setMessage(task.getMessage());
        return vo;
    }
} 