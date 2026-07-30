# Issue #5 (debate-ruled): the ONE justified project rule — everything else
# rides library consumer rules on purpose (hand keeps rot; re-open with the
# Qonversion/ads batch).
#
# Persisted-enum invariant: Engine (DB rows, TranslationRepositoryImpl) and
# the AppConfig FeatureToggle csv cross the app-update boundary as
# name() -> valueOf(). The default optimize file already keeps enum
# values/valueOf; this pins OUR first-party enums explicitly so an R8
# full-mode behaviour change can never silently break History reads or
# feature-toggle parsing.
-keepclassmembers enum com.codeboxlk.tranzlate.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# MLKit translate: release smoke caught a launch NPE inside
# RemoteModelManager.getInstance() (component chain returned null) even though
# the AAR consumer rules kept the registrars — an internal surface the
# library's own rules miss under AGP's R8 full mode. Namespace keep is the
# verified-working mitigation; narrowing it is recorded follow-up work.
-keep class com.google.mlkit.** { *; }
