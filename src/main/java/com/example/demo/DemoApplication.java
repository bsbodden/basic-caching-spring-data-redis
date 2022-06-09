package com.example.demo;

import java.io.Serializable;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.github.javafaker.Faker;

@SpringBootApplication
@EnableCaching
public class DemoApplication {

  private static final Logger log = LoggerFactory.getLogger(DemoApplication.class);

  @Bean
  public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig() //
        .prefixCacheNameWith(this.getClass().getPackageName() + ".") //
        .entryTtl(Duration.ofHours(1)) //
        .disableCachingNullValues();

    return RedisCacheManager.builder(connectionFactory) //
        .cacheDefaults(config) //
        .build();
  }

  @Bean
  CommandLineRunner loadTestData(SomeModelRepo repo) {
    return args -> {
      Faker faker = new Faker(new Locale("en-US"));
      IntStream.rangeClosed(1, 1000).forEach((i) -> {
        repo.save(SomeModel.of(faker.artist().name()));
      });
    };
  }

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplateBuilder().build();
  }

  @Bean
  CommandLineRunner testCaching(SomeModelRepo repo, RestTemplate restTemplate) throws Exception {
    final String url = "http://localhost:8080/api/some/{id}";
    return args -> {
      var someIds = repo.findAll(PageRequest.of(0, 20)).stream().map(SomeModel::getId).toList();
      for (String id : someIds) {
        ResponseEntity<SomeModel> response = restTemplate.getForEntity(url, SomeModel.class, id);
        if (response.getStatusCode() == HttpStatus.OK) {
          log.info("👉 " + response.getBody());
        }
      }
    };
  }

  public static void main(String[] args) {
    SpringApplication.run(DemoApplication.class, args);
  }

}

@RedisHash
class SomeModel implements Serializable {
  private String id;
  private String name;

  public SomeModel() {
  }

  public static SomeModel of(String name) {
    var sm = new SomeModel();
    sm.setName(name);
    return sm;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}

@Repository
interface SomeModelRepo extends PagingAndSortingRepository<SomeModel, String> {
}

@RestController
@RequestMapping("/api/some")
class SomeModelController {
  @Autowired
  SomeModelRepo repo;

  @GetMapping("/{id}")
  @Cacheable("some-cache")
  public SomeModel get(@PathVariable("id") String id) {
    return repo.findById(id).orElse(SomeModel.of("nope"));
  }
}
