package com.cloudrive.service;

import com.cloudrive.service.impl.OssStorageServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StorageServiceFactory {

    private final OssStorageServiceImpl ossStorageService;

    @Autowired
    public StorageServiceFactory(OssStorageServiceImpl ossStorageService) {
        this.ossStorageService = ossStorageService;
    }

    public StorageService getStorageService() {
        return ossStorageService;
    }
} 