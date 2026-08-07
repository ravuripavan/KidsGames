plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

/**
 * Repo-wide enforcement of the suite's hardest constraints (see
 * docs/superpowers/specs/2026-08-06-kids-travel-games-design.md):
 *
 * - No text: no `:games:*` module (aside from the one exemption,
 *   `:games:talktime`) may contain a string resource or a literal passed to
 *   `Text(...)`.
 * - Offline / no microphone: no manifest anywhere in the repo may declare the
 *   `INTERNET` or `RECORD_AUDIO` permission.
 *
 * This is the only mechanical gate for those constraints. Wired into
 * `check` (and therefore `build`) for every subproject so it runs
 * automatically and cannot be skipped by forgetting to invoke it directly.
 */
val textExemptGameModules = setOf("talktime")

val verifyDesignSpecGate = tasks.register("verifyDesignSpecGate") {
    group = "verification"
    description = "Fails the build on no-text violations in :games:* modules (except talktime) " +
        "or on INTERNET/RECORD_AUDIO permissions anywhere in the repo."

    val root = rootDir
    val gamesRoot = File(root, "games")

    doLast {
        val violations = mutableListOf<String>()

        fun isNoiseDir(dir: File): Boolean =
            dir.name == "build" || dir.name == ".gradle" || dir.name == ".git" || dir.name == ".idea"

        if (gamesRoot.isDirectory) {
            gamesRoot.listFiles { f -> f.isDirectory }
                ?.filterNot { it.name in textExemptGameModules }
                ?.forEach { moduleDir ->
                    moduleDir.walkTopDown()
                        .onEnter { dir -> !isNoiseDir(dir) }
                        .filter { it.isFile }
                        .forEach { file ->
                            when {
                                file.name == "strings.xml" -> {
                                    val text = file.readText()
                                    if (Regex("<string[\\s>]").containsMatchIn(text)) {
                                        violations += "no-text: string resource in " +
                                            "${file.relativeTo(root)}"
                                    }
                                }
                                file.extension == "kt" -> {
                                    val text = file.readText()
                                    // Text("literal") or Text(text = "literal")
                                    val textCallWithLiteral = Regex(
                                        """\bText\s*\(\s*(text\s*=\s*)?"[^"]*"""",
                                    )
                                    if (textCallWithLiteral.containsMatchIn(text)) {
                                        violations += "no-text: literal passed to Text(...) in " +
                                            "${file.relativeTo(root)}"
                                    }
                                }
                            }
                        }
                }
        }

        root.walkTopDown()
            .onEnter { dir -> !isNoiseDir(dir) }
            .filter { it.isFile && it.name == "AndroidManifest.xml" }
            .forEach { manifest ->
                val text = manifest.readText()
                if (text.contains("android.permission.INTERNET")) {
                    violations += "permission: INTERNET declared in ${manifest.relativeTo(root)}"
                }
                if (text.contains("android.permission.RECORD_AUDIO")) {
                    violations += "permission: RECORD_AUDIO declared in ${manifest.relativeTo(root)}"
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Design-spec gate failed with ${violations.size} violation(s):\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
    }
}

subprojects {
    afterEvaluate {
        tasks.findByName("check")?.dependsOn(verifyDesignSpecGate)
    }
}
