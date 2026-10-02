plugins {
    id("pe.edu.nova.java.library")
}

description = "Núcleo de persistencia de Nova: la página por cursor, su petición y el códec del cursor."

repositories {
    // Los errores son los de ADR-031, de nova-api-standard, que se publica en el GitHub Packages de su repositorio.
    maven {
        name = "NovaApiStandard"
        url = uri("https://maven.pkg.github.com/ahincho/nova-java-01-api-standard")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("NOVA_PACKAGES_READ_TOKEN") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    // Es parte del contrato: un cursor o un límite inválido es un ApplicationError, y un servicio lo atrapa por su tipo.
    api("pe.edu.nova.java.libs:nova-api-standard:1.1.0")

    testImplementation("com.tngtech.archunit:archunit:1.5.1")
}
