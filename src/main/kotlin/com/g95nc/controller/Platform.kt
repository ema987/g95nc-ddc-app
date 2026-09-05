package com.g95nc.controller

/**
 * Which OS this is running on. One place for the "mac"/"nix"/"nux"/"aix"/"win" substring checks
 * that used to be repeated (each slightly differently - one site checked "nux" but not "aix",
 * for instance) across MonitorController, OnboardingWizard, and Win32Dxva2Controller.
 */
enum class Platform {
    MAC, LINUX, WINDOWS, OTHER;

    companion object {
        fun detect(osName: String = System.getProperty("os.name")): Platform {
            val name = osName.lowercase()
            return when {
                name.contains("mac") -> MAC
                name.contains("win") -> WINDOWS
                name.contains("nix") || name.contains("nux") || name.contains("aix") -> LINUX
                else -> OTHER
            }
        }
    }
}
