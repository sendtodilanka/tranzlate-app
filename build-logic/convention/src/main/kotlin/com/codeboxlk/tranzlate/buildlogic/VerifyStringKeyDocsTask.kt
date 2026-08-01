package com.codeboxlk.tranzlate.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * C-3 gate — every shipped string key must exist in a `STRINGS_*.md` catalogue.
 *
 * C-3 (DECISIONS.md) says `STRINGS_*.md` is the ONLY string-key authority and that missing
 * keys get ADDED to the catalogue. Until this task existed nothing checked it: PR-6 shipped
 * `cd_lang_back` into three locales and into no catalogue, every gate stayed green, and a
 * co-verify lens caught it by reading the PR body. A rule that only holds when a reviewer
 * remembers it is the same shape as the checklist that was already written the second time
 * `main` broke (CLAUDE.md rule 8).
 *
 * Direction: **resource to catalogue only.** The reverse (a documented key with no resource) is
 * deliberately NOT a failure, because in this project the catalogue is written BEFORE the
 * resource — both catalogues carry a `NEW` legend entry meaning "key does not exist — created
 * here" — and because section 7 of `STRINGS_text-translation.md` deliberately keeps retired keys
 * on record with a "why" column. Measured on the commit that introduced this task, a reverse
 * check would have reported 60 catalogue tokens with no resource, essentially all of them
 * old-app provenance citations, `tt_*` test tags, or keys retired on purpose. A gate whose first
 * run is 60 false positives gets switched off. The forward direction has no such ambiguity: a
 * key that ships inside the APK and is in no catalogue is exactly the C-3 breach that happened.
 *
 * A key counts as documented when its exact name appears in a catalogue OUTSIDE
 * `~~strikethrough~~` — in these catalogues strikethrough means retired, so a key that is still
 * shipping while the catalogue says it was removed is a finding, not a pass. Matching is
 * whole-token, so documenting `home_tool_offline` does not silently document
 * `home_tool_offline_sub`.
 *
 * Known limitation, recorded rather than hidden: the task proves a key is *mentioned*, not that
 * the row around it is accurate. It cannot tell a stale `en` value from a fresh one.
 */
@CacheableTask
abstract class VerifyStringKeyDocsTask : DefaultTask() {

    /** Every shipped `strings.xml`, all modules, all non-test source sets, every locale. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceFiles: ConfigurableFileCollection

    /** Every `STRINGS_*.md` catalogue under `docs/`. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogueFiles: ConfigurableFileCollection

    /** Written on success so the task is up-to-date-checkable and leaves evidence. */
    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    /** Only used to print short paths in the failure message. */
    @get:Internal
    abstract val rootDirPath: Property<String>

    @TaskAction
    fun verify() {
        val root = File(rootDirPath.get())
        val catalogues = catalogueFiles.files.sortedBy { it.invariantSeparatorsPath }
        val resources = resourceFiles.files.sortedBy { it.invariantSeparatorsPath }

        // An input that quietly disappeared must fail loudly. A gate whose silence is
        // indistinguishable from a pass is the failure mode CLAUDE.md rule 11 names by hand.
        if (catalogues.isEmpty()) {
            throw GradleException(
                "No STRINGS_*.md catalogue found under docs/. C-3 makes those files the only " +
                    "string-key authority, so with none present this gate cannot verify anything " +
                    "— that is a failure, not a pass. Restore the catalogues, or update the " +
                    "catalogueFiles input of :verifyStringKeyDocs.",
            )
        }
        if (resources.isEmpty()) {
            throw GradleException(
                "No values*/ strings.xml found. The resourceFiles input of :verifyStringKeyDocs " +
                    "no longer matches this repository's layout, so the gate would pass vacuously.",
            )
        }

        val documentedText = catalogues.joinToString("\n") { it.readText() }
            .replace(STRIKETHROUGH, "")

        // key -> the resource files that declare it.
        val declared = linkedMapOf<String, MutableSet<String>>()
        resources.forEach { file ->
            val body = file.readText().replace(XML_COMMENT, "")
            RESOURCE_KEY.findAll(body).forEach { match ->
                declared.getOrPut(match.groupValues[2]) { sortedSetOf() } +=
                    file.relativeToOrSelf(root).invariantSeparatorsPath
            }
        }

        val undocumented = declared.filterKeys { key -> documentedText.doesNotMention(key) }
        if (undocumented.isNotEmpty()) {
            throw GradleException(failureMessage(undocumented, catalogues, root, declared.size))
        }

        writeReport(declared.size, resources, catalogues, root)
    }

    private fun writeReport(
        keyCount: Int,
        resources: List<File>,
        catalogues: List<File>,
        root: File,
    ) {
        val report = reportFile.get().asFile
        report.parentFile?.mkdirs()
        report.writeText(
            buildString {
                appendLine("verifyStringKeyDocs — C-3 string-key authority")
                appendLine("keys checked:   $keyCount")
                appendLine("resource files: ${resources.size}")
                appendLine("catalogues:     ${catalogues.size}")
                catalogues.forEach {
                    appendLine("  " + it.relativeToOrSelf(root).invariantSeparatorsPath)
                }
            },
        )
        logger.lifecycle(
            "verifyStringKeyDocs: $keyCount keys, all documented across " +
                "${catalogues.size} STRINGS_*.md catalogue(s).",
        )
    }

    private fun failureMessage(
        undocumented: Map<String, Set<String>>,
        catalogues: List<File>,
        root: File,
        totalKeys: Int,
    ): String = buildString {
        appendLine(
            "C-3 string-key authority: ${undocumented.size} of $totalKeys shipped keys appear " +
                "in no STRINGS_*.md catalogue.",
        )
        appendLine()
        undocumented.entries
            .groupBy { entry -> entry.value.first().substringBefore("/src/") }
            .toSortedMap()
            .forEach { (module, entries) ->
                appendLine("  $module  (${entries.size})")
                entries.sortedBy { it.key }.forEach { (key, files) ->
                    appendLine("    " + key.padEnd(PAD) + files.first())
                }
                appendLine()
            }
        appendLine("Catalogues searched (${catalogues.size}):")
        catalogues.forEach { appendLine("  " + it.relativeToOrSelf(root).invariantSeparatorsPath) }
        appendLine()
        appendLine(
            "C-3: \"STRINGS_*.md is the ONLY key authority... Missing keys get ADDED to STRINGS.\"",
        )
        appendLine("Two ways forward, neither of them editing this task:")
        appendLine("  - the key is real -> add a row for it to the catalogue that owns the feature")
        appendLine("    (a new docs/specs/00-foundations/STRINGS_<feature>.md is a valid home);")
        appendLine("  - the key is dead -> delete it from values/ and from every values-*/ locale.")
        appendLine(
            "A key counts as documented when its exact name appears outside ~~strikethrough~~; " +
                "strikethrough means retired.",
        )
    }

    private companion object {
        /** `<string name="x">` / `<plurals name="x">`; attribute-order tolerant, `<string-array` excluded. */
        val RESOURCE_KEY = Regex("<(string|plurals)(?=[\\s>])[^>]*\\bname\\s*=\\s*\"([^\"]+)\"")
        val XML_COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
        val STRIKETHROUGH = Regex("~~.*?~~", RegexOption.DOT_MATCHES_ALL)
        const val PAD = 42

        fun String.doesNotMention(key: String): Boolean =
            !Regex("(?<![A-Za-z0-9_])" + Regex.escape(key) + "(?![A-Za-z0-9_])")
                .containsMatchIn(this)
    }
}
