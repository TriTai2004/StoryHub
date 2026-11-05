package story.feature.upload.services.service;

import org.springframework.web.multipart.MultipartFile;

import story.feature.upload.services.dto.res.UploadResult;

public interface MediaStorageService {
    
    UploadResult upload(MultipartFile file);

    void delete(String publicId);
}
