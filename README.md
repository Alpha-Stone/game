## CASE 1 for plugin failure

// 1. PLACE THIS AT THE VERY TOP OF YOUR FILE
buildscript {
repositories {
mavenLocal() // Forces Gradle to look in your local .m2 folder for the plugins
}
dependencies {
// These are the physical JAR packages for your plugins
classpath 'org.springframework.boot:spring-boot-gradle-plugin:4.1.1'
classpath 'io.spring.gradle:dependency-management-plugin:1.1.7'
}
}

// 2. APPLY THE PLUGINS LINKED ABOVE
apply plugin: 'java'
apply plugin: 'org.springframework.boot'
apply plugin: 'io.spring.dependency-management'

group = 'com.drdo'
version = '0.0.1-SNAPSHOT'

java {
toolchain {
languageVersion = JavaLanguageVersion.of(21)
}
}

repositories {
mavenLocal() // Forces Gradle to look in your local .m2 for project dependencies
}

dependencies {
implementation 'org.springframework.boot:spring-boot-starter-kafka'
implementation 'org.springframework.boot:spring-boot-starter-webflux'
implementation 'org.springframework.boot:spring-boot-starter-webmvc'
implementation 'org.apache.kafka:kafka-streams'
compileOnly 'org.projectlombok:lombok'
runtimeOnly 'org.postgresql:postgresql'
annotationProcessor 'org.projectlombok:lombok'
testImplementation 'org.springframework.boot:spring-boot-starter-kafka-test'
testImplementation 'org.springframework.boot:spring-boot-starter-webflux-test'
testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
testCompileOnly 'org.projectlombok:lombok'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
testAnnotationProcessor 'org.projectlombok:lombok'
implementation 'org.apache.kafka:kafka-clients'
}

tasks.named('test') {
useJUnitPlatform()
}


## case 2 

Solution 2: Map the Plugin Repository in settings.gradle
If you prefer to keep the modern plugins { ... } block, you must explicitly tell Gradle's plugin management system to look inside your .m2 directory. By default, it ignores mavenLocal() for plugins unless configured otherwise.
1. Open your settings.gradle file (located in the same project root folder).
2. Add the pluginManagement block at the very top of the file:
   groovy
   pluginManagement {
   repositories {
   mavenLocal() // Tells the plugin engine to search your local .m2 repository
   }
   }

rootProject.name = 'your-project-name'
Use code with caution.
3. Run your terminal command: gradle build --offline
