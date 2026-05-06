plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.3.21"
}

group = "org.j3y"
version = "0.0.1-SNAPSHOT"
description = "Huskers Discord Bot v2"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
	// Playwright for headless browser fetching
	implementation("com.microsoft.playwright:playwright:1.47.0")
	// Jsoup for HTML parsing/stripping
	implementation("org.jsoup:jsoup:1.18.1")
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")

	implementation("com.github.kagkarlsson:db-scheduler-spring-boot-4-starter:16.7.+")
	implementation("com.github.ben-manes.caffeine:caffeine")
	implementation("net.dv8tion:JDA:5.6.+")
	implementation("org.apache.httpcomponents.client5:httpclient5:5.+")
    implementation("org.apache.httpcomponents.core5:httpcore5:5.+")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("org.bytedeco:opencv-platform:4.9.0-1.5.10") // OpenCV for eye detection for deepfry
	implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0") // ImageIO WebP support for deepfry

	runtimeOnly("com.h2database:h2")
	runtimeOnly("com.mysql:mysql-connector-j")
	
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.mockito:mockito-inline:5.2.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
