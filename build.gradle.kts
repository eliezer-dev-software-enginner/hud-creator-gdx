import java.util.Properties

plugins {
    id("java")
    id("maven-publish")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.gradleup.shadow") version "8.3.5"
}

val props = Properties()
file("gradle.properties").inputStream().use { props.load(it) }

group = "megalodonte_app"
version = props.getProperty("appVersion")

repositories {
    mavenCentral()
    mavenLocal()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

javafx {
    version = "25.0.1"

    // javafx.swing only backs SwingFXUtils, used by the skin-preview smoke
    // test (my_app.skin.render.SkinPreviewSnapshot) to dump a PNG for visual
    // review — not used anywhere in the shipped app.
    modules("javafx.controls", "javafx.graphics", "javafx.swing")
}

dependencies {
    implementation("megalodonte:megalodonte-base:1.0.0-beta")
    implementation("megalodonte:megalodonte-components:1.0.0-beta")
    implementation("megalodonte:megalodonte-reactivity:1.0.0-beta")
    implementation("megalodonte:megalodonte-theme:1.0.0-beta")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    // JsonReader/JsonValue only, for reading skin.json in libGDX's own lenient
    // JSON dialect (unquoted keys/values - what Skin Composer actually exports,
    // which a strict parser like Jackson rejects). Pure text parsing, no
    // Gdx.app/LWJGL/graphics context needed - doesn't reopen the Fase 0
    // "no libGDX dependency" decision for anything graphics-related.
    implementation("com.badlogicgames.gdx:gdx:1.14.2")

    implementation("org.kordamp.ikonli:ikonli-core:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-javafx:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-entypo-pack:12.4.0")
    // Entypo has no sun icon (only MOON) - needed for the light/dark theme toggle icon.
    implementation("org.kordamp.ikonli:ikonli-fontawesome5-pack:12.4.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("skinPreviewSnapshot") {
    group = "verification"
    description = "Renders skin regions/9-patches to build/skin-preview-smoke-test.png for visual review (Fase 2)."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("my_app.skin.render.SkinPreviewSnapshot")
}

tasks.register<JavaExec>("homeScreenSnapshot") {
    group = "verification"
    description = "Renders HomeScreen (empty/error/loaded states) to build/home-screen-smoke-test.png for visual review (Fase 3)."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("my_app.HomeScreenSmokeTest")
}

tasks.register<JavaExec>("widgetViewsSnapshot") {
    group = "verification"
    description = "Renders Button/TextButton/Image widgets to build/widget-views-smoke-test.png for visual review (Fase 4)."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("my_app.widget.render.WidgetViewsSnapshot")
}

tasks.register<JavaExec>("canvasControllerSnapshot") {
    group = "verification"
    description = "Places widgets on a Canva via CanvasController (drop math + clamping) to build/canvas-controller-smoke-test.png for visual review (Fase 4)."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("my_app.CanvasControllerSmokeTest")
}

tasks.register<JavaExec>("exportDemo") {
    group = "verification"
    description = "Places a few widgets and exports them for real into ../libgdx-example-game/assets/ui/hud-demo.json (Fase 5)."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("my_app.ExportDemo")
}

application {
    mainClass.set(props.getProperty("appMainClass"))
}

tasks.named<JavaExec>("run") {
    // Reading JAVAFX_MODULES_HOME has to happen in doFirst, not directly in this
    // configuration block: tasks.named { ... } runs at CONFIGURATION time, which
    // Gradle triggers just to build the task graph (e.g. `./gradlew tasks`, or an
    // IDE's Gradle sync) — well before the "run" task actually executes, and
    // possibly in a process that never inherited the shell's environment. That's
    // what caused "undefined variable" errors even when it was set in the shell
    // running `./gradlew run` directly. doFirst only runs when this task executes.
    doFirst {
        val os = System.getProperty("os.name").lowercase()

        val javafxModulesHome = System.getenv("JAVAFX_MODULES_HOME")
            ?: throw GradleException(
                "Environment variable JAVAFX_MODULES_HOME is not set. " +
                        "Point it to the folder containing linux-25.0.1/ and windows-25.0.1/."
            )

        val fxPath = if (os.contains("win")) {
            "$javafxModulesHome/windows-25.0.1/lib"
        } else {
            "$javafxModulesHome/linux-25.0.1/lib"
        }

        jvmArgs = listOf(
            "--module-path", fxPath,
            "--add-modules", "javafx.controls,javafx.graphics",
            "--enable-native-access=ALL-UNNAMED",
            "-Dprism.verbose=true"
        )
    }

    environment("DEV_MODE", "true")
}

tasks.shadowJar {
    dependsOn(tasks.test)
    archiveBaseName.set(props.getProperty("appName"))
    archiveClassifier.set("")
    mergeServiceFiles() // equivalent to ServicesResourceTransformer

    manifest {
        attributes(
            "Main-Class" to props.getProperty("appMainClass")
        )
    }

    // Excludes JavaFX (like the pom.xml used to)
    exclude("org/openjfx/**")

    // 🔥 removes broken signatures (same as in Maven)
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

tasks.jar {
    enabled = false
    archiveBaseName.set(props.getProperty("appName"))

    manifest {
        attributes(
            "Implementation-Title" to "JavaFX ${props.getProperty("appName")} app",
            "Implementation-Version" to project.version,
            "Main-Class" to props.getProperty("appMainClass")
        )
    }

    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}


// Publishing configuration (kept as-is)
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = props.getProperty("appName")
        }
    }
}
