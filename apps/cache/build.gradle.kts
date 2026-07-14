dependencies {
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
}

tasks.withType<Test>().configureEach {
	testLogging {
		showStandardStreams = true
	}
}
