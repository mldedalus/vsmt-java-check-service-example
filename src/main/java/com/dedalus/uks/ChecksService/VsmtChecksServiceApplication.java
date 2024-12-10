package com.dedalus.uks.ChecksService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;


@SpringBootApplication
//@EnableConfigurationProperties(S3IndexerConfig.class)
public class VsmtChecksServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(VsmtChecksServiceApplication.class, args);
	}

	 @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}
