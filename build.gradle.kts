@file:Suppress("UnstableApiUsage")

import com.diffplug.spotless.FormatterFunc
import com.hominux.compress4j.semver.CheckApiCompatibilityTask
import me.champeau.gradle.japicmp.JapicmpTask
import org.gradle.api.publish.maven.MavenPom
import org.jreleaser.model.Active
import java.io.Serializable
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    `jacoco-report-aggregation`
    `java-library`
    `java-test-fixtures`
    `jvm-test-suite`
    `maven-publish`
    jacoco

    alias(libs.plugins.errorprone)
    alias(libs.plugins.git.version)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.spotless)
    id("publishing-conventions")
    id("semver-conventions")
}

val stagingDir: Provider<Directory> = layout.buildDirectory.dir("staging-deploy")
val relocationStagingDir: Provider<Directory> = layout.buildDirectory.dir("staging-deploy-relocation")
val snapshotVersion: String = $$"${describe.tag.version.major}." +
        $$"${describe.tag.version.minor}." +
        $$"${describe.tag.version.patch.next}-SNAPSHOT"

group = "com.hominux"
description = "A simple archiving and compression library for Java."
version = "0.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val examples: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withJavadocJar()
    withSourcesJar()
}

val examplesImplementation: Configuration by configurations
val mockitoAgent: Configuration = configurations.create("mockitoAgent")

dependencies {
    api(libs.commons.io)
    api(libs.jspecify)

    implementation(libs.commons.compress)
    implementation(libs.commons.lang3)
    implementation(libs.slf4j.api)

    compileOnly(libs.org.tukaani.xz)
    compileOnly(libs.com.github.luben.zstd.jni)

    testFixturesApi(platform(libs.jackson.bom))
    testFixturesApi(libs.assertj.core)
    testFixturesApi(libs.commons.compress)
    testFixturesApi(libs.commons.io)
    testFixturesApi(libs.jackson.core)
    testFixturesApi(libs.jspecify)
    testFixturesApi(libs.logback.classic)
    testFixturesApi(libs.logback.core)

    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.jackson.annotations)
    testFixturesImplementation(libs.jackson.databind)
    testFixturesImplementation(libs.mockito.core)

    errorprone(libs.error.prone.core)
    errorprone(libs.nullaway)

    mockitoAgent(libs.mockito.core) { isTransitive = false }
}

testing {
    suites {
        named("test", JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation(platform(libs.junit.bom))

                implementation(libs.archunit)
                implementation(libs.archunit.junit5.api)
                implementation(libs.assertj.core)
                implementation(libs.junit.jupiter.api)
                implementation(libs.junit.jupiter.params)
                implementation(libs.logback.classic)
                implementation(libs.logback.core)
                implementation(libs.mockito.core)
                implementation(libs.mockito.jupiter)
                implementation(libs.jimfs)

                runtimeOnly(libs.archunit.junit5)
                runtimeOnly(libs.error.prone.core)
                runtimeOnly(libs.nullaway)
                runtimeOnly(libs.org.tukaani.xz)
                runtimeOnly(libs.com.github.luben.zstd.jni)
                runtimeOnly(libs.org.brotli.dec)
            }
        }
    }
}

val integrationTest by testing.suites.registering(JvmTestSuite::class) {
    dependencies {
        implementation(platform(libs.junit.bom))
        implementation(project())
        implementation(testFixtures(project()))
        implementation(libs.junit.jupiter.api)
        implementation(libs.junit.jupiter.params)

        runtimeOnly(libs.asm)
        runtimeOnly(libs.org.tukaani.xz)
        runtimeOnly(libs.com.github.luben.zstd.jni)
        runtimeOnly(libs.org.brotli.dec)
    }

    targets.all { testTask.configure {
        shouldRunAfter(tasks.test)
    }}
}

val fuzzTest by testing.suites.registering(JvmTestSuite::class) {
    useJUnitJupiter(libs.versions.junit5.bom)

    dependencies {
        implementation(platform(libs.junit5.bom))
        implementation(project())
        implementation(libs.commons.compress)
        implementation(libs.jazzer.junit)

        runtimeOnly(libs.org.tukaani.xz)
        runtimeOnly(libs.com.github.luben.zstd.jni)
        runtimeOnly(libs.org.brotli.dec)
    }

    targets.all { testTask.configure {
        shouldRunAfter(tasks.test)
        extensions.configure<JacocoTaskExtension> { isEnabled = false }
        if (providers.environmentVariable("JAZZER_FUZZ").isPresent) {
            systemProperty("jazzer.instrument", "com.hominux.compress4j.**,org.apache.commons.compress.**")
        }
    }}
}

val errorProneJvmArgs: List<String> = listOf(
    "api", "code", "comp", "file", "main", "model", "parser", "processing", "tree", "util"
).map { "--add-exports=jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED" } +
        listOf("code", "comp").map { "--add-opens=jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED" }

tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-javaagent:${mockitoAgent.asPath}",
            "--add-opens=java.base/java.util.zip=ALL-UNNAMED"
        )
    })
}

tasks.test {
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        val errorprone = tasks.compileJava.get().options.errorprone
        val options = errorprone.checkOptions.get().map { (name, value) -> "-XepOpt:$name=$value" }
        val checks = errorprone.checks.get().map { (name, severity) -> "-Xep:$name:$severity" }
        errorProneJvmArgs + "-Dcompile.errorprone.args=${(checks + options + errorprone.errorproneArgs.get()).joinToString(" ")}"
    })
}

val apiBaselineVersion: String = providers.gradleProperty("api.baseline").orElse(semver.previousVersion).get()

fun detachedBaselineConfiguration(version: String, classifier: String?): Configuration = configurations.detachedConfiguration(
    dependencies.create(
        listOfNotNull("${project.group}", project.name, version, classifier).joinToString(":") + "@jar"
    )
).apply { isTransitive = false }

val newestDownloadableBaselineVersion: String by lazy {
    val candidates = listOf(apiBaselineVersion).filter { it.isNotEmpty() }
        .plus(semver.releaseVersions.get())
        .distinct()
    val published = candidates.firstOrNull {
        detachedBaselineConfiguration(it, null).incoming.artifactView { lenient(true) }.artifacts.artifacts.isNotEmpty()
    }
    when {
        published == null -> "".also {
            logger.warn("None of the release tags $candidates is published, skipping the API compatibility check")
        }
        published != apiBaselineVersion -> published.also {
            logger.warn("Release $apiBaselineVersion is tagged but not published, comparing the API against $it instead")
        }
        else -> published
    }
}

fun baselineArtifacts(classifier: String?): FileCollection = files({
    newestDownloadableBaselineVersion.takeIf { it.isNotEmpty() }?.let { detachedBaselineConfiguration(it, classifier) } ?: files()
})

fun registerApiComparison(name: String, baseline: FileCollection, jarTask: TaskProvider<Jar>, classpath: FileCollection) =
    tasks.register<JapicmpTask>(name) {
        group = "verification"
        description = "Compares the API of the built jar against the newest published release ($name)."
        onlyIf { newestDownloadableBaselineVersion.isNotEmpty() }
        oldArchives.from(baseline)
        newArchives.from(jarTask)
        oldClasspath.from(classpath)
        newClasspath.from(classpath)
        accessModifier = "protected"
        onlyModified = true
        ignoreMissingClasses = true
        xmlOutputFile = layout.buildDirectory.file("reports/japicmp/$name.xml")
        htmlOutputFile = layout.buildDirectory.file("reports/japicmp/$name.html")
    }

val japicmpMain = registerApiComparison(
    "japicmpMain",
    baselineArtifacts(null),
    tasks.jar,
    sourceSets.main.get().compileClasspath
)
val checkApiCompatibility = tasks.register<CheckApiCompatibilityTask>("checkApiCompatibility") {
    group = "verification"
    description = "Fails when the API changes since the last release ask for a bigger version bump than the commits declare."
    baselineVersion = provider { newestDownloadableBaselineVersion }
    declaredBump = semver.declaredBump
    reports.from(japicmpMain.flatMap { it.xmlOutputFile })
}

dependencyAnalysis {
    issues {
        all {
            onUnusedDependencies {
                exclude("org.junit.jupiter:junit-jupiter")
            }
            onCompileOnly {
                exclude("org.jspecify:jspecify")
            }
            onAny {
                severity("fail")
            }
        }
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial"))
    options.encoding = "UTF-8"
}

tasks.named<JavaCompile>("compileJava") {
    options.errorprone {
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
    }
}

tasks.withType<JavaCompile>().matching { it.name != "compileJava" }.configureEach {
    options.errorprone.enabled = false
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "The Compress4J Project"
        )
    }
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all")
}

tasks.testCodeCoverageReport {
    dependsOn(tasks.test, integrationTest)
    executionData(
        fileTree(layout.buildDirectory).include("jacoco/*.exec")
    )
    reports {
        xml.required = true
        html.required = true
    }
    mustRunAfter(tasks.spotlessCheck, tasks.javadoc)
}

val testCodeCoverageVerification by tasks.registering(JacocoCoverageVerification::class) {
    group = "verification"
    description = "Fails when the aggregated line or branch coverage drops below the configured minimums."
    val report = tasks.testCodeCoverageReport.get()
    executionData(report.executionData)
    classDirectories.setFrom(report.classDirectories)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.93".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
    mustRunAfter(tasks.testCodeCoverageReport)
}

tasks.check {
    dependsOn(
        tasks.buildHealth,
        tasks.spotlessCheck,
        checkApiCompatibility,
        integrationTest,
        tasks.testCodeCoverageReport,
        testCodeCoverageVerification,
        gradle.includedBuild("${rootProject.name}-build-logic").task(":test")
    )
}

if (System.getProperty("os.name").startsWith("Linux")) {
    tasks.check { dependsOn(fuzzTest) }
}

sonar {
    properties {
        property("sonar.projectKey", "hominux_compress4j")
        property("sonar.organization", "hominux")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sources", "src/main/java,src/examples/java,.github/workflows")
        property("sonar.tests", "src/test/java,src/integrationTest/java,src/fuzzTest/java,src/testFixtures/java")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml")
        property(
            "sonar.coverage.exclusions",
            listOf(
                "src/examples/java/**/*",
                "**/*Exception.java"
            )
        )
    }
}

tasks.sonar {
    dependsOn(
        tasks.testCodeCoverageReport,
        tasks.classes,
        tasks.testClasses,
        tasks.named("testFixturesClasses"),
        tasks.named("integrationTestClasses")
    )
}

spotless {
    ratchetFrom("origin/main")
    java {
        toggleOffOn()
        palantirJavaFormat("2.81.0").formatJavadoc(true)
        licenseHeaderFile(rootProject.file(".config/spotless/copyright.java.txt"))
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        custom("Refuse wildcard imports", object : Serializable, FormatterFunc {
            override fun apply(input: String): String {
                if (input.contains("\nimport .*\\*;".toRegex())) {
                    throw AssertionError(
                        "Wildcard imports (e.g., 'import java.util.*;') are not allowed. " +
                                "Please use explicit imports. 'spotlessApply' cannot resolve this issue automatically."
                    )
                }
                return input
            }
        })
    }
    format("javaMisc") {
        target("src/**/package-info.java")
        licenseHeaderFile(rootProject.file(".config/spotless/copyright.java.txt"), "\\/\\*\\*|import |@NullMarked|package ")
    }
}

gitVersioning.apply {
    refs {
        branch("main") {
            version = snapshotVersion
        }
        tag("v(?<version>.*)") {
            version = $$"${ref.version}"
        }
    }

    rev {
        version = snapshotVersion
    }
}

fun MavenPom.centralMetadata() {
    url = "https://hominux.com/compress4j/"
    organization {
        name = "Hominux"
        url = "https://hominux.com"
    }
    issueManagement {
        system = "GitHub"
        url = "https://github.com/hominux/compress4j/issues"
    }
    scm {
        connection = "scm:git:https://github.com/hominux/compress4j.git"
        developerConnection = "scm:git:git@github.com:hominux/compress4j.git"
        url = "https://github.com/hominux/compress4j"
    }
    licenses {
        license {
            name = "Apache-2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            distribution = "repo"
        }
    }
    developers {
        developer {
            id = "austek"
            name = "Ali Ustek"
        }
        developer {
            id = "renasustek"
            name = "Renas Ustek"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            suppressPomMetadataWarningsFor("testFixturesApiElements")
            suppressPomMetadataWarningsFor("testFixturesRuntimeElements")
            pom {
                name = project.name
                description = project.description
                centralMetadata()
                withXml {
                    val dependencies = asNode().get("dependencies") as groovy.util.NodeList
                    (dependencies.first() as groovy.util.Node).appendNode("dependency").apply {
                        appendNode("groupId", "org.tukaani")
                        appendNode("artifactId", "xz")
                        appendNode("version", libs.versions.tukaani.xz.get())
                        appendNode("scope", "compile")
                        appendNode("optional", "true")
                    }
                    (dependencies.first() as groovy.util.Node).appendNode("dependency").apply {
                        appendNode("groupId", "com.github.luben")
                        appendNode("artifactId", "zstd-jni")
                        appendNode("version", libs.versions.zstd.jni.get())
                        appendNode("scope", "compile")
                        appendNode("optional", "true")
                    }
                    (dependencies.first() as groovy.util.Node).appendNode("dependency").apply {
                        appendNode("groupId", "org.brotli")
                        appendNode("artifactId", "dec")
                        appendNode("version", libs.versions.brotli.dec.get())
                        appendNode("scope", "compile")
                        appendNode("optional", "true")
                    }
                }
            }
        }
        create<MavenPublication>("relocation") {
            groupId = "io.github.compress4j"
            artifactId = project.name
            pom {
                packaging = "pom"
                name = project.name
                description = "Relocated to com.hominux:compress4j."
                centralMetadata()
                distributionManagement {
                    relocation {
                        groupId = "com.hominux"
                        artifactId = project.name
                        message = "compress4j moved to the com.hominux group."
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = "staging"
            url = uri(stagingDir.get().toString())
        }
        maven {
            name = "relocationStaging"
            url = uri(relocationStagingDir.get().toString())
        }
    }
}

// Central rejects a deployment that spans namespaces, so the relocation POM is staged and deployed on its own.
tasks.withType<PublishToMavenRepository>().configureEach {
    val isRelocation = publication.name == "relocation"
    onlyIf { isRelocation == (repository.name == "relocationStaging") }
}

configure<org.jreleaser.gradle.plugin.JReleaserExtension> {
    release {
        github {
            skipTag = true
            changelog {
                formatted = Active.ALWAYS
                preset = "conventional-commits"
                links = true
            }
        }
    }
    signing {
        pgp {
            active = Active.ALWAYS
            armored = true
        }
    }
    deploy {
        maven {
            mavenCentral {
                register("sonatype") {
                    active = Active.ALWAYS
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository(stagingDir.get().toString())
                }
                register("sonatypeRelocation") {
                    active = Active.ALWAYS
                    namespace = "io.github.compress4j"
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository(relocationStagingDir.get().toString())
                }
            }
        }
    }
}
