package com.jd.genie.config;

import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.*;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DataSourceConfig {

  @Bean
  @Primary
  @ConfigurationProperties(prefix = "spring.datasource") // H2 主库
  public DataSource h2DataSource() {
    return DataSourceBuilder.create().type(HikariDataSource.class).build();
  }

  @Bean(name = "mysqlDataSource")
  @ConfigurationProperties(prefix = "spring.datasource-mysql") // MySQL 副库
  public DataSource mysqlDataSource() {
    return DataSourceBuilder.create().type(HikariDataSource.class).build();
  }
}