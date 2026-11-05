package story.feature.upload.services.dto.res;

public record UploadResult(
    String publicId,
    String secureUrl,
    long size,
    int width,
    int height
) {
    
}
