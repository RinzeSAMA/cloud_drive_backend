package com.cloudrive.controller;

import com.cloudrive.common.annotation.RateLimit;
import com.cloudrive.common.annotation.RateLimit.Dimension;
import com.cloudrive.common.constant.CommonConstants;
import com.cloudrive.common.constant.ShareConstants;
import com.cloudrive.common.exception.BusinessException;
import com.cloudrive.common.result.Result;
import com.cloudrive.model.dto.ShareAccessDTO;
import com.cloudrive.model.dto.ShareCreateDTO;
import com.cloudrive.model.vo.ShareFileVO;
import com.cloudrive.service.ShareService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 分享管理
 */
@RestController
@Validated
@RequestMapping("/api/shares")
public class ShareController {

    private static final Logger logger = LoggerFactory.getLogger(ShareController.class);

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    /**
     * 创建分享
     */
    @PostMapping
    // 限制用户和IP访问频率
    @RateLimit(dimensions = { Dimension.USER, Dimension.IP }, permitsPerSecond = 5.0, timeout = 1000)
    public Result<ShareFileVO> createShare(@Valid @RequestBody ShareCreateDTO dto) {
        ShareFileVO vo = shareService.createShare(
                dto.getFileId(),
                dto.getExpireTime(),
                dto.getPassword());
        return Result.success(vo);
    }

    /**
     * 获取当前用户的所有分享
     */
    @GetMapping
    @RateLimit(dimensions = { Dimension.USER }, permitsPerSecond = 10.0, timeout = 500)
    public Result<List<ShareFileVO>> getSharedFiles() {
        List<ShareFileVO> sharedFiles = shareService.getSharedFiles();
        return Result.success(sharedFiles);
    }

    /**
     * 验证分享密码并获取访问令牌
     */
    @PostMapping("/{shareCode}/verification")
    @RateLimit(dimensions = { Dimension.IP }, permitsPerSecond = 3.0, timeout = 1000)
    public Result<ShareFileVO> verifyShare(@PathVariable String shareCode, @Valid @RequestBody ShareAccessDTO dto) {
        // 验证密码并获取分享信息
        ShareFileVO shareFile = shareService.accessShare(shareCode, dto.getPassword());

        // 生成分享令牌
        String token = shareService.generateShareToken(shareCode, dto.getPassword());      
        
        // 创建分享特定的Cookie
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getResponse();
        ResponseCookie cookie = ResponseCookie.from(ShareConstants.Token.SHARE_TOKEN_COOKIE_PREFIX + shareCode, token)
                .secure(true)
                .path("/")
                .maxAge(CommonConstants.Time.ONE_DAY) // 24小时
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return Result.success(shareFile);
    }

    /**
     * 访问分享文件
     */
    @PostMapping("/{shareCode}")
    @RateLimit(dimensions = { Dimension.IP }, permitsPerSecond = 10.0, timeout = 1000)
    public Result<ShareFileVO> getShare(@PathVariable String shareCode,
            @Valid @RequestBody(required = false) ShareAccessDTO dto) {
        logger.debug("Accessing share: shareCode={}", shareCode);
        
        // 获取当前请求
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest();
        
        // 尝试从Cookie中获取令牌
        String token = extractShareTokenFromCookies(shareCode, request);

        // 如果有令牌，尝试使用令牌访问
        if (token != null && shareService.validateShareToken(shareCode, token)) {
            logger.debug("Valid token found for share code: {}", shareCode);
            try {
                ShareFileVO shareFile = shareService.accessShareByToken(shareCode, token);
                return Result.success(shareFile);
            } catch (BusinessException e) {
                // 令牌无效，继续下一步验证
            }
        }

        // 对于需要密码的分享，password可能为空，由service内部检查并抛出异常
        String password = dto != null ? dto.getPassword() : null;
        ShareFileVO shareFile = shareService.accessShare(shareCode, password);
        return Result.success(shareFile);
    }

    /**
     * 取消分享
     */
    @DeleteMapping("/{shareCode}")
    @RateLimit(dimensions = { Dimension.USER }, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> cancelShare(@PathVariable String shareCode) {
        shareService.cancelShare(shareCode);
        return Result.success();
    }

    /**
     * 下载分享文件
     */
    @GetMapping("/{shareCode}/content")
    @RateLimit(dimensions = { Dimension.IP }, permitsPerSecond = 2.0, timeout = 1000)
    public ResponseEntity<byte[]> downloadSharedFile(
            @PathVariable String shareCode) {
        // 从Cookie中获取令牌
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest();
        String token = extractShareTokenFromCookies(shareCode, request);

        // 获取文件内容
        byte[] fileContent = shareService.downloadSharedFile(shareCode, token);
        
        // 设置响应头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", shareService.getFilename(shareCode));
        headers.setContentLength(fileContent.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(fileContent);
    }

    private String extractShareTokenFromCookies(String shareCode, HttpServletRequest request) {

        if (shareCode == null || request == null) {
            logger.warn("Invalid parameters: shareCode={}, request={}", shareCode, request);
            return null;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            logger.debug("No cookies found in request");
            return null;
        }

        String cookieName = ShareConstants.Token.SHARE_TOKEN_COOKIE_PREFIX + shareCode;
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
