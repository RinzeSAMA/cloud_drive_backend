//package com.cloudrive.repository;
//
//import com.cloudrive.model.entity.ShareRecord;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Modifying;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.stereotype.Repository;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Optional;
//
//@Repository
//public interface ShareRecordRepository extends JpaRepository<ShareRecord, Long> {
//    /**
//     * 根据分享码查找分享记录。
//     */
//    Optional<ShareRecord> findByShareCode(String shareCode);
//
//
//    /**
//     * 增加分享记录的访问次数。
//     */
//    @Modifying
//    @Query("UPDATE ShareRecord s SET s.visitCount = s.visitCount + 1 WHERE s.id = ?1")
//    void incrementVisitCount(Long id);
//
//    /**
//     * 查询过期的分享记录。
//     */
//    List<ShareRecord> findByExpireTimeBeforeAndIsExpiredFalse(LocalDateTime expireTime);
//
//    /**
//     * 根据用户ID查询分享记录。
//     */
//    List<ShareRecord> findByUserIdOrderByCreateTimeDesc(Long userId);
//}