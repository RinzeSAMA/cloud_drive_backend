package com.cloudrive.service;

import com.cloudrive.common.result.Result;
import com.cloudrive.model.common.FileUploadInfo;
import com.cloudrive.model.dto.FileQueryDTO;
import com.cloudrive.model.vo.FileListVO;
import com.cloudrive.model.vo.FileListPageVO;
import com.cloudrive.model.vo.UploadUrlsVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文件服务接口
 */
public interface FileService {

    /**
     * 下载文件
     */
    byte[] downloadFile(Long fileId);

    /**
     * 分页查询文件列表
     *
     */
    FileListPageVO getFileListWithPagination(FileQueryDTO fileQueryDTO);

    /**
     * 逻辑删除文件,支持批量
     */
    void deleteFileToRecycleBin(List<Long> fileIds);

    /**
     * 彻底删除文件（逻辑删除->物理删除）
     */
    void deleteFileForPermanent(List<Long> fileId);
    /**
     * 重命名文件
     */
    void renameFile(Long fileId, String newFilename);

    /**
     * 搜索文件
     */
    List<FileListVO> searchFiles(String keyword);

    /**
     * 获取文件名
     */
    String getFilename(Long fileId);

    /**
     * 获取文件内容
     * @param fileId 文件ID
     * @return 文件内容字节数组
     */
    byte[] getFileContent(Long fileId);

    /**
     * 新建文件夹并返回ID
     */
    Long createFolderWithId(String folderName, Long parentId, Long userId);

    FileListPageVO getRecycleListFiles(FileQueryDTO queryDTO);


    Result<FileUploadInfo> checkFileByMd5(String md5);

    Result<UploadUrlsVO> initMultipartUpload(FileUploadInfo fileUploadInfo);

    Result<String> mergeMultipartUpload(String md5,Long parentId);

    ResponseEntity<byte[]> downloadMultipartFile(Long id, HttpServletRequest request, HttpServletResponse response) throws IOException;
}