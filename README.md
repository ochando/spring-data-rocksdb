# spring-data-rocksdb


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
