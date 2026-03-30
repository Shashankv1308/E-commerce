# Getting Started

## Setup

### Prerequisites
- [Java 21](https://adoptium.net/) (JDK, not just JRE)
- [Docker](https://www.docker.com/products/docker-desktop/) (for Redis)
- [PostgreSQL](https://www.postgresql.org/download/) (native install — DB data must survive container restarts)

### 1. Configure environment variables
```bash
cp .env.example .env
# Open .env and set DB_PASSWORD to your local PostgreSQL password
```

### 2. Create the PostgreSQL databases (one-time)
```bash
# macOS/Linux
createdb ecommerce_db
createdb ecommerce_test

# Windows (run in psql or pgAdmin)
# CREATE DATABASE ecommerce_db;
# CREATE DATABASE ecommerce_test;
```

### 3. Start Redis
```bash
docker compose up -d
```

### 4. Run the app
```bash
# macOS/Linux
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

### 5. Run the tests
```bash
# macOS/Linux
./mvnw test

# Windows
mvnw.cmd test
```

---

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.1/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.0.1/maven-plugin/build-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.0.1/reference/web/servlet.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.0.1/reference/data/sql.html#data.sql.jpa-and-spring-data)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.

