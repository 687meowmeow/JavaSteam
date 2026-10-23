import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    // PATCH (wepwawet fork): REMOVED `maven-publish` from root plugins.
    // On JitPack's Gradle 9.5.1, having `maven-publish` here conflicts with
    // something else (likely the java-gradle-plugin in buildSrc) and fails
    // with "Plugin with id 'org.gradle.maven-publish' was already requested
    // at line 7". The subprojects each apply maven-publish themselves via
    // id("maven-publish"), so the root project doesn't need it.
    alias(libs.plugins.kotlin.dokka)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kotlinter)
    // PATCH (wepwawet fork): REMOVED alias(libs.plugins.maven.publish)
    // This was the io.github.gradle-nexus.publish-plugin (for publishing to
    // Maven Central via Sonatype). On JitPack, this plugin conflicts with
    // the built-in `maven-publish` plugin (line 7) and the build fails with
    // "Plugin with id 'org.gradle.maven-publish' was already requested".
    // JitPack uses the built-in maven-publish plugin, so we don't need the
    // nexus publish plugin here.
    alias(libs.plugins.protobuf.gradle)
    id("jacoco")
    id("signing")
    projectversiongen
    steamlanguagegen
    rpcinterfacegen
}

allprojects {
    group = "in.dragonbra"
    version = "1.8.1-SNAPSHOT"
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.java.get()))
    }
}

/* Protobufs */
protobuf.protoc {
    artifact = libs.protobuf.protoc.get().toString()
}


/* Testing */
tasks.test {
    useJUnitPlatform()
    testLogging {
        events = setOf(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
        )
    }
}

/* Test Reporting */
jacoco.toolVersion = libs.versions.jacoco.get()
tasks.jacocoTestReport {
    reports {
        xml.required = false
        html.required = false
    }
}

/* Java-Kotlin Docs */
dokka {
    moduleName.set("JavaSteam")
    dokkaSourceSets.main {
        suppressGeneratedFiles.set(false) // Allow generated files to be documented.
        perPackageOption {
            // Deny most of the generated files.
            matchingRegex.set("in.dragonbra.javasteam.(protobufs|enums|generated).*")
            suppress.set(true)
        }
    }
}

// Make sure Maven Publishing gets javadoc
val javadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.dokkaGenerate)
    archiveClassifier.set("javadoc")
    from(layout.buildDirectory.dir("dokka/html"))
}
artifacts {
    archives(javadocJar)
}

/* Configuration */
configurations {
    configureEach {
        // Only allow junit 5
        exclude("junit", "junit")
        exclude("org.junit.vintage", "junit-vintage-engine")
    }
}

/* Source Sets */
sourceSets.main {
    java.srcDirs(
        // builtBy() fixes gradle warning "Execution optimizations have been disabled for task"
        files("build/generated/source/steamd/main/java").builtBy("generateSteamLanguage"),
        files("build/generated/source/javasteam/main/java").builtBy("generateProjectVersion", "generateRpcMethods")
    )
}

/* Basic Java 9 JPMS support */
tasks.jar {
    manifest {
        attributes["Automatic-Module-Name"] = "in.dragonbra.javasteam"
    }
    exclude("**/*.proto")
}

/* Tasks */
tasks["check"].dependsOn("jacocoTestReport")
tasks["compileJava"].dependsOn("generateSteamLanguage", "generateProjectVersion", "generateRpcMethods")
tasks["compileKotlin"].dependsOn("generateSteamLanguage", "generateProjectVersion", "generateRpcMethods")
tasks["generateRpcMethods"].dependsOn("extractProto", "extractIncludeProto")
afterEvaluate {
    tasks.named("lintKotlin") {
        dependsOn("formatKotlin")
    }
}

/* Kotlinter */
tasks.withType<LintTask> {
    val generatedFile = "${File.separator}build${File.separator}generated"
    exclude { it.file.path.contains(generatedFile) }
}

tasks.withType<FormatTask> {
    val generatedFile = "${File.separator}build${File.separator}generated"
    exclude { it.file.path.contains(generatedFile) }
}

/* JDK and Mockito self attachment fix */
// "Mockito is currently self-attaching to enable the inline-mock-maker.
//      This will no longer work in future releases of the JDK"
val mockitoAgent = configurations.create("mockitoAgent")
tasks.withType<Test> {
    doFirst {
        jvmArgs("-javaagent:${mockitoAgent.asPath}")
    }
}

dependencies {
    mockitoAgent(libs.test.mock.core) { isTransitive = false }

    implementation(libs.bundles.ktor)
    implementation(libs.bundles.okHttp)
    implementation(libs.commons.lang3)
    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlin.stdib)
    implementation(libs.protobuf.java)
    compileOnly(libs.xz)
    compileOnly(libs.zstd)

    testImplementation(platform(libs.tests.junit.bom))
    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.tests.junit.platform)
}

/* Artifact publishing */
// PATCH (wepwawet fork): REMOVED nexusPublishing { ... } block — this was
// the io.github.gradle-nexus.publish-plugin DSL which we removed from the
// plugins block above. JitPack doesn't need this (it uses Gradle's built-in
// maven-publish plugin). Removing this block fixes the
// 'Unresolved reference: sonatype/nexusUrl/ossrhUsername' errors.
//
// ALSO REMOVED the root project's publishing { ... } and signing { ... }
// blocks — they referenced maven-publish which we removed from the root
// plugins block above (it was conflicting with buildSrc's java-gradle-plugin
// auto-applied maven-publish). The subprojects each have their own
// publishing { ... } and signing { ... } blocks, so the root doesn't need
// them. The root project is just a container — it doesn't publish anything
// itself.
