# spring-data-rocksdb

## Using KeyValueTemplate
Using KeyValueTemplate, we can perform the same operations as we did with the repository.

### Spring configuration bean
```java
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

### Creating an Entity
Let's create an Employee entity:

```java
@KeySpace("employees")
public class Employee {
 
    @Id
    private Integer id;
 
    private String name;
 
    private String department;
 
    private String salary;
 
    // constructors/ standard getters and setters
 
}
```

### Saving an Object
Let’s see how to save a new Employee object to the data store using a template:

```java
Employee employee = new Employee(1, "Mile", "IT", "5000");
keyValueTemplate.insert(employee);
```

### Retrieving an Existing Object
We can verify the insertion of the object by fetching it from the structure using template:

```java
Optional<Employee> savedEmployee = keyValueTemplate
  .findById(id, Employee.class);
```

### Updating an Existing Object
Unlike CrudRepository, the template provides a dedicated method to update an object:

```java
employee.setName("Jacek");
keyValueTemplate.update(employee);
```

### Deleting an Existing Object
We can delete an object with a template:

```java
keyValueTemplate.delete(id, Employee.class);
```

### Fetch All Objects
We can fetch all the saved objects using a template:

```java
Iterable<Employee> employees = keyValueTemplate
  .findAll(Employee.class);
```

### Sorting the Objects
In addition to the basic functionality, the template also supports KeyValueQuery for writing custom queries.

For example, we can use a query to get a sorted list of Employees based on their salary:

```java
KeyValueQuery<Employee> query = new KeyValueQuery<Employee>();
query.setSort(new Sort(Sort.Direction.DESC, "salary"));
Iterable<Employee> employees 
  = keyValueTemplate.find(query, Employee.class);
```


### pom.xml
```xml
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
