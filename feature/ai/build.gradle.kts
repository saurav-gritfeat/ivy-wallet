plugins {
    id("ivy.feature")
}

android {
    namespace = "com.ivy.ai"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)
    implementation(projects.shared.data.model)
    implementation(projects.shared.domain)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)
    implementation(projects.temp.legacyCode)
    implementation(projects.temp.oldDesign)

    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.hilt)
    ksp(libs.hilt.compiler)
    implementation(libs.bundles.room)
    implementation(libs.datastore)
    implementation(libs.timber)
    implementation(libs.mediapipe.genai)

    testImplementation(libs.bundles.testing)
}

