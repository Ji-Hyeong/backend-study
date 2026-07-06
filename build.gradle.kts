import org.gradle.api.plugins.JavaPluginExtension
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm") version "1.9.25" apply false
	kotlin("plugin.spring") version "1.9.25" apply false
	kotlin("plugin.jpa") version "1.9.25" apply false
	id("org.springframework.boot") version "3.5.3" apply false
	id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
	group = "com.jihyeong.lab"
	version = "0.0.1-SNAPSHOT"

	repositories {
		mavenCentral()
	}
}

subprojects {
	apply(plugin = "org.jetbrains.kotlin.jvm")
	apply(plugin = "org.jetbrains.kotlin.plugin.spring")
	apply(plugin = "org.jetbrains.kotlin.plugin.jpa")
	apply(plugin = "org.springframework.boot")
	apply(plugin = "io.spring.dependency-management")

	extensions.configure<JavaPluginExtension> {
		toolchain {
			languageVersion = JavaLanguageVersion.of(17)
		}
	}

	dependencies {
		add("implementation", "org.springframework.boot:spring-boot-starter-actuator")
		add("implementation", "org.springframework.boot:spring-boot-starter-validation")
		add("implementation", "org.springframework.boot:spring-boot-starter-web")
		add("implementation", "com.fasterxml.jackson.module:jackson-module-kotlin")
		add("implementation", "org.jetbrains.kotlin:kotlin-reflect")

		add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
		add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5")
		add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
	}

	extensions.configure<KotlinJvmProjectExtension> {
		compilerOptions {
			freeCompilerArgs.addAll("-Xjsr305=strict")
		}
	}

	extensions.configure<AllOpenExtension> {
		annotation("jakarta.persistence.Entity")
		annotation("jakarta.persistence.MappedSuperclass")
		annotation("jakarta.persistence.Embeddable")
	}

	tasks.withType<Test>().configureEach {
		useJUnitPlatform()
	}

	tasks.withType<KotlinCompile>().configureEach {
		compilerOptions {
			freeCompilerArgs.add("-Xjsr305=strict")
		}
	}
}
