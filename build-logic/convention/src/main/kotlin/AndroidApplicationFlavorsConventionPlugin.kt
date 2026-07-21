import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `tranzlate.android.application.flavors` — white-label + deterministic-engine flavor
 * mechanics (plan §4 + §6).
 *
 * Dimensions: `brand` x `engine`.
 *  - `engine` flavors (`prod` | `fake`) are created HERE (mechanics, not brand data).
 *    `fake` adds NO applicationIdSuffix (plan §6.5 — Maestro appId match; escape hatch
 *    `.fake` suffix documented there, not enabled).
 *  - `brand` flavors are declared as declarative data in `:app/build.gradle.kts`
 *    (plan §4 R1 — brand list never lives in a .kt file; new app = new flavor block).
 *
 * Guards (plan §6.6):
 *  - every `fake` + `release` variant is killed via beforeVariants;
 *  - androidTest is disabled on `fake` variants (plan §6.3 — fake determinism comes from
 *    the production-installed FakeTranslateModule, not instrumentation).
 */
class AndroidApplicationFlavorsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<ApplicationExtension> {
                flavorDimensions += listOf(DIMENSION_BRAND, DIMENSION_ENGINE)
                productFlavors {
                    create(ENGINE_PROD) { dimension = DIMENSION_ENGINE }
                    create(ENGINE_FAKE) { dimension = DIMENSION_ENGINE }
                }
            }
            extensions.configure<ApplicationAndroidComponentsExtension> {
                beforeVariants { variantBuilder ->
                    val isFake = variantBuilder.productFlavors.contains(DIMENSION_ENGINE to ENGINE_FAKE)
                    if (isFake) {
                        if (variantBuilder.buildType == "release") {
                            variantBuilder.enable = false
                        }
                        variantBuilder.enableAndroidTest = false
                    }
                }
            }
        }
    }

    companion object {
        const val DIMENSION_BRAND = "brand"
        const val DIMENSION_ENGINE = "engine"
        const val ENGINE_PROD = "prod"
        const val ENGINE_FAKE = "fake"
    }
}
