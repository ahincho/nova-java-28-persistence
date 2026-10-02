plugins {
    id("pe.edu.nova.java.library")
}

description = "Conector de persistencia con Spring Boot: la entidad auditable, el scroll por cursor y la traducción de errores."

val springBootVersion = "4.0.8"
val cqrsVersion = "1.0.0"

repositories {
    // Los errores son los de nova-api-standard, que se publica en el GitHub Packages de su repositorio.
    maven {
        name = "NovaApiStandard"
        url = uri("https://maven.pkg.github.com/ahincho/nova-java-01-api-standard")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("NOVA_PACKAGES_READ_TOKEN") ?: System.getenv("GITHUB_TOKEN")
        }
    }
    // Los comportamientos de los buses y el actor de la auditoría son los de CQRS (ADR-053).
    maven {
        name = "NovaCqrs"
        url = uri("https://maven.pkg.github.com/ahincho/nova-java-27-cqrs")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("NOVA_PACKAGES_READ_TOKEN") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    api(project(":nova-persistence"))

    // Spring Boot, Spring Data JPA y Spring MVC los trae el servicio; el starter solo compila contra ellos, con las
    // versiones de su BOM. CQRS y Spring Security se usan solo si el servicio los tiene.
    compileOnly(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.data:spring-data-jpa")
    compileOnly("jakarta.persistence:jakarta.persistence-api")
    compileOnly("org.springframework:spring-orm")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("pe.edu.nova.java.libs:nova-cqrs:$cqrsVersion")
    annotationProcessor(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.security:spring-security-core")
    testImplementation("pe.edu.nova.java.starters:nova-cqrs-spring-boot-starter:$cqrsVersion")
    testRuntimeOnly("com.h2database:h2")
}
