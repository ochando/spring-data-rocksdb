# spring-data-rocksdb

```
@Configuration
public class Config {

    @Bean
    public KeyValueOperations keyValueTemplate() {
        return new KeyValueTemplate(keyValueAdapter());
    }

    @Bean
    public KeyValueAdapter keyValueAdapter() {
        return new RocksDBKeyValueAdapter();
    }
}
```


```
		<dependency>
			<groupId>org.springframework.data</groupId>
			<artifactId>spring-data-keyvalue</artifactId>
		</dependency>
        
        <dependency>
			<groupId>org.rocksdb</groupId>
			<artifactId>rocksdbjni</artifactId>
			<version>${rocksdb.version}</version>
		</dependency>
```
