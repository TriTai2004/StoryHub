package story.feature.upload.services.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import lombok.RequiredArgsConstructor;
import story.feature.upload.services.dto.res.UploadResult;
import story.feature.upload.services.service.MediaStorageService;

@Service 
@RequiredArgsConstructor 
public class CloudinaryStorageService implements MediaStorageService{

    private final Cloudinary cloudinary;

    @Override
    public UploadResult upload(MultipartFile file) {

        validateImg(file);
        
        try {
            Map<?,?> uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                    "resource_type", "image"
                )
            );

            String publicId = (String) uploadResult.get("public_id");
            String secureUrl = (String) uploadResult.get("secure_url");
            long size = ((Number) uploadResult.get("bytes")).longValue();
            int width = ((Number) uploadResult.get("width")).intValue();
            int height = ((Number) uploadResult.get("height")).intValue();

            return new UploadResult(publicId, secureUrl, size, width, height);

        } catch (Exception e) {
            throw new IllegalStateException("Cannot upload image", e);
        }

    }

    @Override
    public void delete(String publicId) {
        
        if(publicId == null || publicId.isBlank()){
            throw new IllegalArgumentException("Public ID is required");
        }

        try {
            Map<?,?> result = cloudinary.uploader().destroy(
                publicId, 
                ObjectUtils.asMap(
                    "resource_type", "image",
                    "invalidate", true
                )
            );

            String status = (String) result.get("result");

            if(!"ok".equals(status) && !"not found".equals(status)){
                throw new IllegalStateException("Unexpected image deletion result");
            }

        } catch (Exception e) {
            throw new IllegalStateException("Cannot delete image", e);

        }

    }

    private void validateImg(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }

        if (file.getSize() > 5L * 1024 * 1024) {
            throw new IllegalArgumentException("Image size must not exceed 5MB");
        }

        try (InputStream input = file.getInputStream();
            ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {

            if (imageInput == null) {
                throw new IllegalArgumentException("Cannot read image");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Unsupported or invalid image");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0 || height <= 0 || width > 4096 || height > 4096) {
                    throw new IllegalArgumentException(
                        "Image dimensions must be between 1 and 4096px"
                    );
                }

                // Decode after checking dimensions to validate image content.
                if (reader.read(0) == null) {
                    throw new IllegalArgumentException("Invalid image content");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read image file", e);
        }
    }
    
}
