// package sms.com.sms;

// import org.hibernate.metamodel.mapping.ModelPartContainer;
// import org.springframework.boot.test.context.TestConfiguration;
// import org.springframework.context.annotation.Bean;
// import org.testcontainers.containers.MySQLContainer;
// import org.testcontainers.utility.DockerImageName;

// @TestConfiguration(proxyBeanMethods = false)
// class TestcontainersConfiguration {

// 	@Bean
// 	@ServiceConnection
// 	ModelPartContainer<?> mysqlContainer() {
// 		return new MySQLContainer<>(DockerImageName.parse("mysql:latest"));
// 	}

// }
