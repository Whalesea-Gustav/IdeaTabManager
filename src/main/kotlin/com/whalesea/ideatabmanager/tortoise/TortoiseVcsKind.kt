package com.whalesea.ideatabmanager.tortoise

/** External Windows clients that can present a commit dialog for a working copy. */
enum class TortoiseVcsKind(
    val displayName: String,
    val executableName: String,
    val installationDirectory: String,
    val environmentVariable: String,
    val registryKeys: List<String>,
) {
    SVN(
        displayName = "TortoiseSVN",
        executableName = "TortoiseProc.exe",
        installationDirectory = "TortoiseSVN",
        environmentVariable = "TORTOISEPROC_PATH",
        registryKeys = listOf("HKLM\\SOFTWARE\\TortoiseSVN", "HKLM\\SOFTWARE\\WOW6432Node\\TortoiseSVN"),
    ),
    GIT(
        displayName = "TortoiseGit",
        executableName = "TortoiseGitProc.exe",
        installationDirectory = "TortoiseGit",
        environmentVariable = "TORTOISEGITPROC_PATH",
        registryKeys = listOf("HKLM\\SOFTWARE\\TortoiseGit", "HKLM\\SOFTWARE\\WOW6432Node\\TortoiseGit"),
    ),
}
