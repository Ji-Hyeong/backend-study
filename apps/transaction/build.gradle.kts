dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.kafka:spring-kafka")
	runtimeOnly("com.h2database:h2")
	testImplementation("org.testcontainers:postgresql")
	testImplementation("org.wiremock:wiremock-standalone:3.13.1")
	testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
	testRuntimeOnly("org.postgresql:postgresql")
}

tasks.withType<Test>().configureEach {
	testLogging {
		showStandardStreams = true
	}
}
