import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.io.File
import java.util.Properties

/**
 * `tranzlate.android.application.signing` — release signing for the Play PRODUCTION
 * update track (launch plan `docs/plan/launch-signing-aab.md`).
 *
 * WHY THIS EXISTS: `applicationId` = `com.codeboxlk.tranzlate.offlinetranslator` is the
 * SAME id as the owner's live Play listing (previously shipped at versionCode 4). Play
 * only accepts an update signed with the SAME certificate, so the release variant must
 * carry the existing upload key.
 *
 * SECRETS: nothing sensitive lives in VCS. The build reads a gitignored pointer file
 * (`<rootDir>/keystore.properties`, override with `-PkeystorePropertiesFile=<path>`)
 * holding `storePassword` / `keyPassword` / `keyAlias` / `storeFile`. The private key
 * itself is expected to sit OUTSIDE the repo (absolute `storeFile`), so the binary can
 * never be committed even by accident. `.gitignore` blocks both layers regardless.
 *
 * CI SAFETY (the load-bearing behaviour): when the pointer file is ABSENT — CI, a fresh
 * clone, another dev's machine — signing is skipped and the release variant builds
 * UNSIGNED. `./gradlew build` (which assembles `tranzlateProdRelease`, keeping R8 on
 * every PR per issue #5) therefore stays green without any secret. What is NOT tolerated
 * is a pointer file that exists but is broken: that means someone intended to sign, and
 * silently emitting an unsigned artifact they might upload is the worse failure, so that
 * case fails the build loudly.
 *
 * WHITE-LABEL: the config is attached to the `release` BUILD TYPE, so it applies to every
 * brand today. When a second brand ships under its own Play listing + own key, override
 * `signingConfig` inside that brand's flavor block (flavor beats build type) and give the
 * pointer file brand-scoped keys.
 */
class AndroidApplicationSigningConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val credentials = ReleaseSigningCredentials.load(this)
            extensions.configure<ApplicationExtension> {
                if (credentials == null) {
                    logger.lifecycle(
                        "[signing] No $PROPERTIES_FILE_NAME — release builds will be UNSIGNED. " +
                            "This is expected on CI; a Play upload needs the real key.",
                    )
                    return@configure
                }
                signingConfigs.create(RELEASE) {
                    storeFile = credentials.storeFile
                    storePassword = credentials.storePassword
                    keyAlias = credentials.keyAlias
                    keyPassword = credentials.keyPassword
                }
                buildTypes.getByName(RELEASE).signingConfig = signingConfigs.getByName(RELEASE)
                logger.lifecycle("[signing] Release signing armed from $PROPERTIES_FILE_NAME.")
            }
        }
    }

    private companion object {
        const val RELEASE = "release"
        const val PROPERTIES_FILE_NAME = "keystore.properties"
    }
}

/**
 * The four values a release `signingConfig` needs, resolved from the gitignored pointer
 * file. Deliberately a plain holder — never logged, never written to a build output.
 */
internal class ReleaseSigningCredentials(
    val storeFile: File,
    val storePassword: String,
    val keyPassword: String,
    val keyAlias: String,
) {
    companion object {
        private const val PROPERTIES_FILE_NAME = "keystore.properties"
        private const val OVERRIDE_PROPERTY = "keystorePropertiesFile"
        private const val KEY_STORE_FILE = "storeFile"
        private const val KEY_STORE_PASSWORD = "storePassword"
        private const val KEY_PASSWORD = "keyPassword"
        private const val KEY_ALIAS = "keyAlias"

        /** Returns null when the pointer file is absent — the CI / unsigned-release path. */
        fun load(project: Project): ReleaseSigningCredentials? {
            val propertiesFile = resolvePropertiesFile(project)
            if (!propertiesFile.isFile) return null

            val properties = Properties()
            propertiesFile.inputStream().use(properties::load)

            val missing = REQUIRED_KEYS.filter { properties.getProperty(it).isNullOrBlank() }
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "${propertiesFile.path} is missing required signing keys: " +
                        "${missing.joinToString()}. Fix it, or delete the file to build unsigned.",
                )
            }

            val storeFile = resolveStoreFile(project, properties.getProperty(KEY_STORE_FILE))
            if (!storeFile.isFile) {
                throw GradleException(
                    "Keystore not found at ${storeFile.path} (storeFile in ${propertiesFile.path}). " +
                        "Fix the path, or delete the file to build unsigned.",
                )
            }

            return ReleaseSigningCredentials(
                storeFile = storeFile,
                storePassword = properties.getProperty(KEY_STORE_PASSWORD),
                keyPassword = properties.getProperty(KEY_PASSWORD),
                keyAlias = properties.getProperty(KEY_ALIAS),
            )
        }

        private val REQUIRED_KEYS =
            listOf(KEY_STORE_FILE, KEY_STORE_PASSWORD, KEY_PASSWORD, KEY_ALIAS)

        private fun resolvePropertiesFile(project: Project): File {
            val override = project.providers.gradleProperty(OVERRIDE_PROPERTY).orNull
            return when {
                override.isNullOrBlank() -> File(project.rootDir, PROPERTIES_FILE_NAME)
                else -> File(override).takeIf(File::isAbsolute) ?: File(project.rootDir, override)
            }
        }

        /** Absolute paths win; relative ones resolve against the repo root, not `:app`. */
        private fun resolveStoreFile(project: Project, rawPath: String): File =
            File(rawPath).takeIf(File::isAbsolute) ?: File(project.rootDir, rawPath)
    }
}
