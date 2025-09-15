plugins {
    `java-library`
}

group = "city.bit"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21)) // Paper/Purpur 1.21 потребує Java 21
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Purpur сумісний з Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    // Тести (залишаємо за замовчуванням; можна не використовувати)
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

// Ресурси (plugin.yml, messages_*.yml, config.yml) автоматично підхоплюються зі src/main/resources

tasks.jar {
    // Нормальна назва артефакту
    archiveBaseName.set("BitCityAuthPlugin")
    archiveVersion.set(project.version.toString())
    // без classifier, щоб отримати BitCityAuthPlugin-0.1.0.jar
}
