dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	runtimeOnly("com.h2database:h2")
}

tasks.withType<Test>().configureEach {
	testLogging {
		showStandardStreams = true
	}
}
