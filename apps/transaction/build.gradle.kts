dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	runtimeOnly("com.h2database:h2")
	testImplementation("org.wiremock:wiremock-standalone:3.13.1")
}

tasks.withType<Test>().configureEach {
	testLogging {
		showStandardStreams = true
	}
}
