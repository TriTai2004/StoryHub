package story.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import story.config.properties.CloudinaryProperties;

@Configuration 
public class ClaudinaryConfig {

    @Bean 
    public Cloudinary cloudinary(CloudinaryProperties properties){
        return new Cloudinary(
            ObjectUtils.asMap(
                "cloud_name", properties.cloudName(),
                "api_key", properties.apiKey(),
                "api_secret", properties.apiSecret(),
                "secure", true
            )
        );
    }
    
}
