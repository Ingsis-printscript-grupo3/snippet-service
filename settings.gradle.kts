plugins {
    // baja el JDK 21 si la maquina no lo tiene, asi el toolchain no depende de lo que haya instalado
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "snippet-service"
