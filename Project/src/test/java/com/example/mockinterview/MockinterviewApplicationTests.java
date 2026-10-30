package com.example.mockinterview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

// Boots the full context, so it needs Postgres (and GEMINI_API_KEY) available, e.g. via
// `docker compose up postgres redis`. Run with RUN_INTEGRATION_TESTS=true.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class MockinterviewApplicationTests {

	@Test
	void contextLoads() {
	}

}
