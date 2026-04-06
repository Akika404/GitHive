package me.akika.githive;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan({"me.akika.githive.auth.mapper", "me.akika.githive.namespace.mapper"})
public class GitHiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitHiveApplication.class, args);
    }

}
