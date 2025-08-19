package com.cloudrive.common.mq.listener;

import com.cloudrive.common.mq.constants.MQConstants;
import com.cloudrive.common.util.storage.MinioUtil;
import com.cloudrive.mapper.FileInfoMapper;

import com.cloudrive.service.impl.FileServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * OSS物理删除操作消息监听
 */
@Component
public class OSSDeleteListener {

    private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);

    @Autowired
    private FileInfoMapper fileInfoMapper;

    @Autowired
    private MinioUtil minioUtil;


    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MQConstants.OSS_DELETE_QUEUE_NAME,durable = "true"),
            exchange = @Exchange(name = MQConstants.OSS_DELETE_EXCHANGE_NAME),
            key = MQConstants.OSS_DELETE_KEY
    ))
    public void listenOssDelete(String object){
        //在删除对应OSS文件之前，先检查是否有其他引用（相同哈希值的文件），状态为未彻底删除（0,1,2）
        long referenceCount = fileInfoMapper.countByPathAndIsDeletedFalse(object);
        // 如果没有当前文件引用，则从存储中删除
        if (referenceCount < 1) {
            minioUtil.deleteObject(object);
            logger.info("从存储中删除了文件: {}", object);
        } else {
            logger.info("不执行物理删除，因为文件路径: {}, 当前引用计数: {}", object, referenceCount);
        }
    }
}
