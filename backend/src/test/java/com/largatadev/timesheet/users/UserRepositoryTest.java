package com.largatadev.timesheet.users;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class UserRepositoryTest {

	@Container
	static final PostgreSQLContainer postgres =
			new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

	@DynamicPropertySource
	static void datasourceProps(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	UserRepository userRepository;

	@Test
	void allFourSeededUsersAreQueryableByUsername() {
		List<String> usernames = List.of("admin", "member1", "member2", "member3");

		for (String username : usernames) {
			Optional<User> found = userRepository.findByUsername(username);
			assertThat(found).as("user '%s' should exist", username).isPresent();
		}
	}

	@Test
	void exactlyOneSeededUserHasAdminRole() {
		Optional<User> admin = userRepository.findByUsername("admin");

		assertThat(admin).isPresent();
		assertThat(admin.get().getRole()).isEqualTo(Role.ADMIN);
		assertThat(userRepository.findByUsername("member1").get().getRole()).isEqualTo(Role.MEMBER);
		assertThat(userRepository.findByUsername("member2").get().getRole()).isEqualTo(Role.MEMBER);
		assertThat(userRepository.findByUsername("member3").get().getRole()).isEqualTo(Role.MEMBER);
	}

	@Test
	void lookupForNonExistentUsernameReturnsEmpty() {
		Optional<User> found = userRepository.findByUsername("nobody-with-this-name");

		assertThat(found).isEmpty();
	}

	@Test
	void storedPasswordIsARealBcryptHashNotPlaintext() {
		Optional<User> admin = userRepository.findByUsername("admin");

		assertThat(admin).isPresent();
		assertThat(admin.get().getPasswordHash()).startsWith("$2a$");
		assertThat(admin.get().getPasswordHash()).doesNotContain("changeme123");
	}
}
