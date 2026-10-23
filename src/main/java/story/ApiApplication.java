package story;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import story.config.properties.CookieProperties;
import story.config.properties.JwtProperties;

@SpringBootApplication
@EnableConfigurationProperties({CookieProperties.class, JwtProperties.class})
public class ApiApplication {

	public static void main(String[] args) {
		if ("Asia/Saigon".equals(TimeZone.getDefault().getID())) {
			TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
		}
		SpringApplication.run(ApiApplication.class, args);
	}

}
