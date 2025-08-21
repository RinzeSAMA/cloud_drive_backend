package com.cloudrive.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudrive.common.constant.CommonConstants;
import com.cloudrive.common.enums.ErrorCode;
import com.cloudrive.common.enums.FileStatus;
import com.cloudrive.common.mq.constants.MQConstants;
import com.cloudrive.common.result.Result;
import com.cloudrive.common.util.*;
import com.cloudrive.common.util.storage.MinioUtil;
import com.cloudrive.config.minio.MinioConfigInfo;
import com.cloudrive.convert.FileConvertUtil;
import com.cloudrive.mapper.FileInfoMapper;
import com.cloudrive.model.common.FileUploadInfo;
import com.cloudrive.model.dto.FileQueryDTO;
import com.cloudrive.model.entity.FileInfoEntity;
import com.cloudrive.model.entity.UserEntity;
import com.cloudrive.model.vo.FileListVO;
import com.cloudrive.model.vo.FileListPageVO;
import com.cloudrive.model.vo.UploadUrlsVO;
import com.cloudrive.service.*;
import io.minio.GetObjectResponse;
import io.minio.StatObjectResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.UUID;

import static com.cloudrive.common.constant.CommonConstants.File.BUFFER_SIZE;

/**
 * 文件服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileServiceImpl implements FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);

    private final FileInfoMapper fileInfoMapper;


    private final RabbitTemplate rabbitTemplate;

    private final ThreadPoolTaskExecutor mqSendExecutor;

    private final MinioUtil minioUtil;

    private final RedisUtil redisUtil;

    private final MinioConfigInfo minioConfigInfo;




    /**
     * 文件列表查询
     */
    @Override
    public FileListPageVO getFileListWithPagination(FileQueryDTO queryDTO) {
        // 一次性查询分页数据和总数
        Page<FileInfoEntity> pageResult = queryDTO.getParentId() == null
                ? fileInfoMapper.findByUserIdAndParentIdIsNullAndIsDeletedFalseWithPagination(queryDTO)
                : fileInfoMapper.findByUserIdAndParentIdAndIsDeletedFalseWithPagination(queryDTO);
        // 转换为VO
        List<FileListVO> fileListVOs = pageResult.getRecords().stream()
                .map(FileConvertUtil::toFileListVO)
                .collect(Collectors.toList());
        return new FileListPageVO(fileListVOs, pageResult.getTotal(), (int) pageResult.getPages(), queryDTO.getPageNo(), queryDTO.getPageSize());
    }

    /**
     * 回收站列表查询
     */
    @Override
    public FileListPageVO getRecycleListFiles(FileQueryDTO queryDTO) {
        Page<FileInfoEntity> pageResult = fileInfoMapper.selectRecycleListFiles(queryDTO);
        List<FileListVO> fileListVOs = pageResult.getRecords().stream()
                .map(FileConvertUtil::toFileListVO)
                .collect(Collectors.toList());
        return new FileListPageVO(fileListVOs, pageResult.getTotal(), (int) pageResult.getPages(), queryDTO.getPageNo(), queryDTO.getPageSize());
    }

    @Override
    public Result<FileUploadInfo> checkFileByMd5(String md5) {
        log.info("查询md5: <{}> 在redis是否存在", md5);
        FileUploadInfo fileUploadInfo = (FileUploadInfo)redisUtil.get(md5);
        if (fileUploadInfo != null) {
            List<Integer> listParts = minioUtil.getListParts(fileUploadInfo.getObject(), fileUploadInfo.getUploadId());
            fileUploadInfo.setListParts(listParts);
            return Result.http(CommonConstants.StatusCode.UPLOADING,"文件上传中",fileUploadInfo);
        }
        log.info("redis中不存在md5: <{}> 查询mysql是否存在", md5);
        FileInfoEntity file = fileInfoMapper.selectOne(new LambdaQueryWrapper<FileInfoEntity>().eq(FileInfoEntity::getMd5, md5).eq(FileInfoEntity::getIsDeleted,0));
        if (file != null) {
            log.info("mysql中存在md5: <{}> 的文件 该文件已上传至minio 秒传直接过", md5);
            FileUploadInfo dbFileInfo = BeanCopyUtils.copyBean(file, FileUploadInfo.class);
            return Result.http(CommonConstants.StatusCode.SUCCESS,"文件上传成功！",dbFileInfo);
        }

        return Result.http(CommonConstants.StatusCode.NOT_UPLOAD,null,null);
    }

    /**
     * 初始化上传
     */
    @Override
    public Result<UploadUrlsVO> initMultipartUpload(FileUploadInfo fileUploadInfo) {
        FileUploadInfo redisFileUploadInfo = (FileUploadInfo)redisUtil.get(fileUploadInfo.getMd5());
        // 若 redis 中有该 md5 的记录，以 redis 中为主
        String object;
        if (redisFileUploadInfo != null) {
            fileUploadInfo = redisFileUploadInfo;
            object = redisFileUploadInfo.getObject();
        } else {
            String originFileName = fileUploadInfo.getOriginFileName();
            String suffix = FileUtil.extName(originFileName);
            String fileName = FileUtil.mainName(originFileName);
            // 对文件重命名，并以年月日文件夹格式存储
            String nestFile = DateUtil.format(LocalDateTime.now(), "yyyy/MM/dd");
            object = nestFile + "/" + fileName + "_" + fileUploadInfo.getMd5() + "." + suffix;

            fileUploadInfo.setObject(object).setType(suffix);
        }

        UploadUrlsVO urlsVO;
        // 单文件上传
        if (fileUploadInfo.getChunkCount() == 1) {
            log.info("当前分片数量 <{}> 单文件上传", fileUploadInfo.getChunkCount());
            urlsVO = minioUtil.getUploadObjectUrl(fileUploadInfo.getContentType(), object);
        } else {
            // 分片上传
            log.info("当前分片数量 <{}> 分片上传", fileUploadInfo.getChunkCount());
            urlsVO = minioUtil.initMultiPartUpload(fileUploadInfo, object);
        }
        fileUploadInfo.setUploadId(urlsVO.getUploadId());

        // 存入 redis （单片存 redis 唯一用处就是可以让单片也入库，因为单片只有一个请求，基本不会出现问题）
        redisUtil.set(fileUploadInfo.getMd5(), fileUploadInfo, minioConfigInfo.getBreakpointTime(), TimeUnit.DAYS);
        return Result.success(urlsVO);
    }

    @Override
    public Result<String> mergeMultipartUpload(String md5,Long parentId) {
        FileUploadInfo redisFileUploadInfo = (FileUploadInfo)redisUtil.get(md5);

        String url = StrUtil.format("{}/{}/{}", minioConfigInfo.getEndpoint(), minioConfigInfo.getBucket(), redisFileUploadInfo.getObject());
        FileInfoEntity files = BeanCopyUtils.copyBean(redisFileUploadInfo, FileInfoEntity.class);
        files.setUrl(url)
                .setFilename(redisFileUploadInfo.getOriginFileName())
                .setIsFolder(false)
                .setUserId(UserContext.getCurrentUserId())
                .setParentId(parentId)
                .setBucket(minioConfigInfo.getBucket())
                .setIsDeleted(0)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());


        Integer chunkCount = redisFileUploadInfo.getChunkCount();
        // 分片为 1 ，不需要合并，否则合并后看返回的是 true 还是 false
        boolean isSuccess = chunkCount == 1 || minioUtil.mergeMultipartUpload(redisFileUploadInfo.getObject(), redisFileUploadInfo.getUploadId());
        if (isSuccess) {
            fileInfoMapper.insert(files);
            redisUtil.del(md5);
            return Result.success("文件上传成功！");

        }
        return Result.error("文件上传失败！");
    }

    @Override
    public ResponseEntity<byte[]> downloadMultipartFile(Long id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        // redis 缓存当前文件信息，避免分片下载时频繁查库
        FileInfoEntity file = null;
        FileInfoEntity redisFile = (FileInfoEntity) redisUtil.get(String.valueOf(id));
        if (redisFile == null) {
            FileInfoEntity dbFile = fileInfoMapper.selectById(id);
            if (dbFile == null) {
                return null;
            } else {
                file = dbFile;
                redisUtil.set(String.valueOf(id), file, 1, TimeUnit.DAYS);
            }
        } else {
            file = redisFile;
        }

        String range = request.getHeader("Range");
        String fileName = file.getOriginFileName();
        log.info("下载文件的 object <{}>", file.getObject());
        // 获取 bucket 桶中的文件元信息，获取不到会抛出异常
        StatObjectResponse objectResponse = minioUtil.statObject(file.getObject());
        long startByte = 0; // 开始下载位置
        long fileSize = objectResponse.size();
        long endByte = fileSize - 1; // 结束下载位置
        log.info("文件总长度：{}，当前 range：{}", fileSize, range);

        BufferedOutputStream os = null; // buffer 写入流
        GetObjectResponse stream = null; // minio 文件流

        // 存在 range，需要根据前端下载长度进行下载，即分段下载
        // 例如：range=bytes=0-52428800
        if (range != null && range.contains("bytes=") && range.contains("-")) {
            range = range.substring(range.lastIndexOf("=") + 1).trim(); // 0-52428800
            String[] ranges = range.split("-");
            // 判断range的类型
            if (ranges.length == 1) {
                // 类型一：bytes=-2343 后端转换为 0-2343
                if (range.startsWith("-")) endByte = Long.parseLong(ranges[0]);
                // 类型二：bytes=2343- 后端转换为 2343-最后
                if (range.endsWith("-")) startByte = Long.parseLong(ranges[0]);
            } else if (ranges.length == 2) { // 类型三：bytes=22-2343
                startByte = Long.parseLong(ranges[0]);
                endByte = Long.parseLong(ranges[1]);
            }
        }

        // 要下载的长度
        // 确保返回的 contentLength 不会超过文件的实际剩余大小
        long contentLength = Math.min(endByte - startByte + 1, fileSize - startByte);
        // 文件类型
        String contentType = request.getServletContext().getMimeType(fileName);

        // 解决下载文件时文件名乱码问题
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        fileName = new String(fileNameBytes, 0, fileNameBytes.length, StandardCharsets.ISO_8859_1);

        // 响应头设置---------------------------------------------------------------------------------------------
        // 断点续传，获取部分字节内容：
        response.setHeader("Accept-Ranges", "bytes");
        // http状态码要为206：表示获取部分内容,SC_PARTIAL_CONTENT,若部分浏览器不支持，改成 SC_OK
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setContentType(contentType);
        response.setHeader("Last-Modified", objectResponse.lastModified().toString());
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        response.setHeader("Content-Length", String.valueOf(contentLength));
        // Content-Range，格式为：[要下载的开始位置]-[结束位置]/[文件总大小]
        response.setHeader("Content-Range", "bytes " + startByte + "-" + endByte + "/" + objectResponse.size());
        response.setHeader("ETag", "\"".concat(objectResponse.etag()).concat("\""));
        response.setContentType("application/octet-stream;charset=UTF-8");

        try {
            // 获取文件流
            stream = minioUtil.getObject(objectResponse.object(), startByte, contentLength);
            os = new BufferedOutputStream(response.getOutputStream());
            // 将读取的文件写入到 OutputStream
            byte[] bytes = new byte[BUFFER_SIZE];
            long bytesWritten = 0;
            int bytesRead = -1;
            while ((bytesRead = stream.read(bytes)) != -1) {
                if (bytesWritten + bytesRead >= contentLength) {
                    os.write(bytes, 0, (int)(contentLength - bytesWritten));
                    break;
                } else {
                    os.write(bytes, 0, bytesRead);
                    bytesWritten += bytesRead;
                }
            }
            os.flush();
            response.flushBuffer();
            // 返回对应http状态
            return new ResponseEntity<>(bytes, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (os != null) os.close();
            if (stream != null) stream.close();
        }
        return null;
    }

    /**
     * 直链形式下载
     */
    @Override
    public String downloadByPreUrl(Long id) {
        // 分为单文件下载和文件夹下载两种方案
        FileInfoEntity files = fileInfoMapper.selectById(id);

        // 单文件下载，直接拿直链返回即可，
        if(!files.getIsFolder()){
            String url = minioUtil.downloadByPreUrl(files.getObject(),files.getFilename());
            return url;
        }

        //TODO 如果是文件夹，需要打包再下载
        return null;
    }


    /**
     * 逻辑删除（到回收站）
     */
    @Override
    @Transactional
    public void deleteFileToRecycleBin(List<Long> fileIds) {
        UserEntity currentUser = UserContext.getCurrentUser();
        // 1. 批量查询 fileIds 对应的文件
        List<FileInfoEntity> fileList = fileInfoMapper.selectList(
                Wrappers.<FileInfoEntity>lambdaQuery()
                        .in(FileInfoEntity::getId, fileIds)
                        .eq(FileInfoEntity::getUserId, currentUser.getId())
                        .eq(FileInfoEntity::getIsDeleted, FileStatus.NORMAL.getCode())
        );
        // 如果根据传入的数组，查出来为空；或者查出来的数据不同，则说明少查了，上抛异常
        ExceptionUtil.throwIf(fileList.isEmpty() || fileList.size()!=fileIds.size() , ErrorCode.FILE_NOT_FOUND);
        List<Long> delFileIdList = new ArrayList<>();// 先保存所有要逻辑删除的文件id

        // 2.  递归查找当前文件的子文件及子文件夹,加入delFileList

        // 加入要查找的文件状态枚举集合（此处为正常）
        List<Integer> status = new ArrayList<>();
        status.add(FileStatus.NORMAL.getCode());
        for (FileInfoEntity fileInfo : fileList) {
            findAllSubFolderFileList(delFileIdList,currentUser.getId(),fileInfo.getId(),status);
        }

        // 3.  批量更新delFileList，状态为2（物理待删除）
        fileInfoMapper.update(
                new FileInfoEntity(){{
                    setIsDeleted(2);
                    setDeleteTime(LocalDateTime.now());
                    setUpdatedAt(LocalDateTime.now());
                }},
                Wrappers.<FileInfoEntity>lambdaQuery()
                        .in(FileInfoEntity::getId, delFileIdList)
        );

       //4.   批量更新原始fileIds，状态为1（逻辑删除）
        fileInfoMapper.update(
                new FileInfoEntity(){{
                    setIsDeleted(1);
                    setDeleteTime(LocalDateTime.now());
                    setUpdatedAt(LocalDateTime.now());
                }},
                Wrappers.<FileInfoEntity>lambdaQuery()
                        .in(FileInfoEntity::getId, fileIds)
        );
    }

    /**
     * 物理删除（删除OSS对应存储）
     */
    @Override
    @Transactional
    public void deleteFileForPermanent(List<Long> fileIds) {
        UserEntity currentUser = UserContext.getCurrentUser();
        // 1. 批量查询 fileIds 对应的文件
        List<FileInfoEntity> fileList = fileInfoMapper.selectList(
                Wrappers.<FileInfoEntity>lambdaQuery()
                        .in(FileInfoEntity::getId, fileIds)
                        .eq(FileInfoEntity::getUserId, currentUser.getId())
                        .eq(FileInfoEntity::getIsDeleted, FileStatus.RECYCLED.getCode())
        );
        // 如果根据传入的数组，查出来为空；或者查出来的数据不同，则说明少查了，上抛异常
        ExceptionUtil.throwIf(fileList.isEmpty() || fileList.size()!=fileIds.size() , ErrorCode.FILE_NOT_FOUND);
        // 保存所有要物理删除的文件id
        List<Long> delFileIdList = new ArrayList<>();

        // 2.  递归查找当前文件的子文件及子文件夹,加入delFileList
        // 加入要查找的文件状态枚举集合
        List<Integer> status = new ArrayList<>();
        status.add(FileStatus.DELETE.getCode());// 待删除的数据（状态为2）
        for (FileInfoEntity fileInfo : fileList) {
            findAllSubFolderFileList(delFileIdList,currentUser.getId(),fileInfo.getId(), status);
        }

        // 3.  筛选非文件夹的文件，得到path值集合，供后续OSS删除
        List<String> objectList = fileInfoMapper.selectList(
                Wrappers.<FileInfoEntity>lambdaQuery()
                        .eq(FileInfoEntity::getIsFolder, false)
                        .in(FileInfoEntity::getId, delFileIdList)
        ).stream().map(FileInfoEntity::getObject).toList();

        // 4.  批量更新delFileList，状态为3（已删除）
        fileInfoMapper.update(
                new FileInfoEntity(){{
                    setIsDeleted(3);
                    setDeleteTime(LocalDateTime.now());
                    setUpdatedAt(LocalDateTime.now());
                }},
                Wrappers.<FileInfoEntity>lambdaQuery()
                        .in(FileInfoEntity::getId, delFileIdList)
        );
        // 5. 使用线程池，发送物理删除的消息到消息队列,异步处理物理删除操作，提高用户体验
        for (String object : objectList) {
            mqSendExecutor.execute(() -> {
                rabbitTemplate.convertAndSend(
                        MQConstants.OSS_DELETE_EXCHANGE_NAME,
                        MQConstants.OSS_DELETE_KEY,
                        object
                );
            });
        }

    }

    @Override
    @Transactional
    public void renameFile(Long fileId, String newFilename) {
        UserEntity currentUser = UserContext.getCurrentUser();
        FileInfoEntity fileInfo = getAndValidateFile(fileId, currentUser);

        fileInfo.setFilename(newFilename);
        fileInfo.setUpdatedAt(LocalDateTime.now());
        fileInfoMapper.updateById(fileInfo);
    }

    @Override
    public List<FileListVO> searchFiles(String keyword) {
        Long userId = UserContext.getCurrentUserId();
        List<FileInfoEntity> fileInfos = fileInfoMapper.searchByFilename(userId, keyword);
        return fileInfos.stream().map(FileConvertUtil::toFileListVO).collect(Collectors.toList());
    }

    //获取文件流
    @Override
    public byte[] getFileContent(Long fileId) {
//        FileInfoEntity fileInfo = getAndValidateFile(fileId, UserContext.getCurrentUser());
//        return retrieveFileContent(fileInfo);
        return null;
    }



    @Override
    @Transactional
    public Long createFolderWithId(String folderName, Long parentId, Long userId) {
        List<FileInfoEntity> siblings = parentId == null
            ? fileInfoMapper.findByUserIdAndParentIdIsNullAndIsDeletedFalse(userId)
            : fileInfoMapper.findByUserIdAndParentIdAndIsDeletedFalse(userId, parentId);
        boolean exists = siblings.stream()
            .anyMatch(f -> Boolean.TRUE.equals(f.getIsFolder()) && f.getFilename().equals(folderName));
        ExceptionUtil.throwIf(exists, ErrorCode.FILE_ALREADY_EXISTS);
        FileInfoEntity folder = new FileInfoEntity();
        folder.setFilename(folderName);
        folder.setOriginFileName(folderName);
        folder.setUserId(userId);
        folder.setParentId(parentId);
        folder.setIsFolder(true);
        folder.setIsDeleted(0);
        folder.setCreatedAt(LocalDateTime.now());
        folder.setUpdatedAt(LocalDateTime.now());
        folder.setSize(0L);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String path = CommonConstants.File.FILE_PATH_PREFIX + userId + "/" + uuid + "/";
        folder.setUrl(path);
        fileInfoMapper.insert(folder);
        return folder.getId();
    }


    /* ---------------- 私有工具 ---------------- */
    private FileInfoEntity getAndValidateFile(Long fileId, UserEntity currentUser) {
        FileInfoEntity fileInfo = fileInfoMapper.selectById(fileId);
        ExceptionUtil.throwIf(fileInfo == null, ErrorCode.FILE_NOT_FOUND);
        ExceptionUtil.throwIf(!fileInfo.getUserId().equals(currentUser.getId()), ErrorCode.NO_PERMISSION);
        ExceptionUtil.throwIf(fileInfo.getIsDeleted() != 0, ErrorCode.FILE_NOT_FOUND);

        return fileInfo;
    }

    /**递归查找指定文件夹下所有子文件夹 */
    private void findAllSubFolderFileList(List<Long> fileIdList,Long userId,Long parentId,List<Integer> status){
        // 每次递归走到这时，会加入当前层的文件id,不管是文件还是文件夹
        fileIdList.add(parentId);
        // 如果是文件夹，递归查询文件夹结构，（包含子文件和子文件夹）
        List<FileInfoEntity> fileInfos = fileInfoMapper.selectList(
                Wrappers.<FileInfoEntity>lambdaQuery()
                        .eq(FileInfoEntity::getUserId, userId)
                        .eq(FileInfoEntity::getParentId, parentId)
                        .in(FileInfoEntity::getIsDeleted,status)
        );
        for(FileInfoEntity fileInfo : fileInfos){
            findAllSubFolderFileList(fileIdList,userId, fileInfo.getId(),status);
        }
    }
}
