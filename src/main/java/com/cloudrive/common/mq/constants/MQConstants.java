package com.cloudrive.common.mq.constants;

/**
 * 消息队列常量
 */
public interface MQConstants {

    /** 文件OSS物理删除操作*/
    // 交换机
    String OSS_DELETE_EXCHANGE_NAME = "oss.delete.direct";
    // 队列
    String OSS_DELETE_QUEUE_NAME = "oss.delete.queue";
    //KEY
    String OSS_DELETE_KEY = "oss.delete.key";
}
