package com.zyt.medconsensus.config;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        String address = "redis://" + redisHost + ":" + redisPort;
        if (StringUtils.hasText(redisPassword)) {
            config.useSingleServer().setAddress(address).setPassword(redisPassword);
        } else {
            config.useSingleServer().setAddress(address);
        }
        return Redisson.create(config);
    }
}
