dependencies {
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("com.auth0:java-jwt:4.5.0")
	testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<Test>().configureEach {
	testLogging {
		showStandardStreams = true
	}
}
