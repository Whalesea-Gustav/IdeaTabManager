package com.whalesea.ideatabmanager.tortoise

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.whalesea.ideatabmanager.model.TabGroupRecord
import com.whalesea.ideatabmanager.model.TabReference
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Finds installed external clients and opens their commit dialogs on a pooled thread. */
object TortoiseCommitService {
    private val log = Logger.getInstance(TortoiseCommitService::class.java)
    private val targetCache = ConcurrentHashMap<GroupCacheKey, CachedTargets>()

    fun availableTargets(group: TabGroupRecord): List<TortoiseCommitTarget> = classifiedTargets(group)
        .filter { TortoiseClientLocator.find(it.kind) != null }

    fun availableTargets(reference: TabReference): List<TortoiseCommitTarget> = TortoiseWorkingCopyClassifier
        .classifyReferences(listOf(reference))
        .filter { TortoiseClientLocator.find(it.kind) != null }

    /** Prepares expensive working-copy and client discovery before a user opens a group context menu. */
    fun warmUp(groups: Collection<TabGroupRecord>) {
        groups.forEach(::availableTargets)
    }

    private fun classifiedTargets(group: TabGroupRecord): List<TortoiseCommitTarget> {
        val key = GroupCacheKey(group.id, group.updatedAtEpochMs, group.tabs.map { it.fileUrl })
        targetCache[key]?.takeUnless(CachedTargets::isExpired)?.let { return it.targets }

        val targets = TortoiseWorkingCopyClassifier.classifyReferences(group.tabs)
        targetCache[key] = CachedTargets(targets, System.nanoTime())
        targetCache.keys.removeIf { cachedKey -> cachedKey.id == group.id && cachedKey != key }
        return targets
    }

    fun launch(project: Project, target: TortoiseCommitTarget) {
        val immutableTarget = target.copy(paths = target.paths.toList())
        ApplicationManager.getApplication().executeOnPooledThread {
            val executable = TortoiseClientLocator.find(immutableTarget.kind)
            if (executable == null) {
                notify(project, "${immutableTarget.kind.displayName} was not found. Install it or refresh the group menu.", NotificationType.ERROR)
                return@executeOnPooledThread
            }

            var invocation: TortoiseCommitInvocation? = null
            try {
                invocation = TortoiseCommitInvocationBuilder.build(immutableTarget.kind, immutableTarget.paths)
                ProcessBuilder(listOf(executable.toString()) + invocation.arguments).start()
                log.info("Opened ${immutableTarget.kind.displayName} commit dialog for ${immutableTarget.fileCount} path(s).")
            } catch (exception: Exception) {
                invocation?.deletePathFileIfPresent()
                log.warn("Could not open ${immutableTarget.kind.displayName} commit dialog.", exception)
                val detail = exception.message ?: exception.javaClass.simpleName
                notify(project, "Could not open ${immutableTarget.kind.displayName} commit dialog: $detail", NotificationType.ERROR)
            }
        }
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                NotificationGroupManager.getInstance().getNotificationGroup("Tab Groups")
                    .createNotification(message, type)
                    .notify(project)
            }
        }
    }

    private data class GroupCacheKey(val id: String, val updatedAtEpochMs: Long, val tabUrls: List<String>)

    private data class CachedTargets(val targets: List<TortoiseCommitTarget>, val cachedAtNanos: Long) {
        fun isExpired(): Boolean = System.nanoTime() - cachedAtNanos >= TARGET_CACHE_TTL_NANOS
    }

    private val TARGET_CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(20)
}

internal object TortoiseClientLocator {
    private val executableCache = ConcurrentHashMap<TortoiseVcsKind, CachedExecutable>()

    fun find(kind: TortoiseVcsKind): Path? {
        executableCache[kind]?.takeUnless(CachedExecutable::isExpired)?.let { cached ->
            return cached.path?.takeIf { path -> Files.isRegularFile(path) }
        }
        synchronized(this) {
            executableCache[kind]?.takeUnless(CachedExecutable::isExpired)?.let { cached ->
                return cached.path?.takeIf { path -> Files.isRegularFile(path) }
            }
            val discovered = discover(kind)
            executableCache[kind] = CachedExecutable(discovered, System.nanoTime())
            return discovered
        }
    }

    private fun discover(kind: TortoiseVcsKind): Path? {
        if (!isWindows()) return null
        val candidates = mutableListOf<Path>()
        val environment = System.getenv()
        addCandidate(candidates, environment[kind.environmentVariable])
        listOf("ProgramFiles", "ProgramFiles(x86)").forEach { variable ->
            environment[variable]?.takeIf(String::isNotBlank)?.let { directory ->
                addCandidate(candidates, Path.of(directory, kind.installationDirectory, "bin", kind.executableName).toString())
            }
        }
        kind.registryKeys.firstNotNullOfOrNull(::queryRegistryProcPath)?.let { addCandidate(candidates, it) }
        environment["PATH"]?.split(java.io.File.pathSeparator)?.forEach { directory ->
            if (directory.isNotBlank()) addCandidate(candidates, Path.of(directory, kind.executableName).toString())
        }
        return candidates.asSequence()
            .map { it.toAbsolutePath().normalize() }
            .firstOrNull { Files.isRegularFile(it) && it.fileName.toString().equals(kind.executableName, ignoreCase = true) }
    }

    private fun addCandidate(candidates: MutableList<Path>, value: String?) {
        if (value.isNullOrBlank()) return
        runCatching { candidates.add(Path.of(value.trim())) }
    }

    private fun queryRegistryProcPath(key: String): String? {
        var process: Process? = null
        return try {
            process = ProcessBuilder("reg.exe", "query", key, "/v", "ProcPath").redirectErrorStream(true).start()
            if (!process.waitFor(2, TimeUnit.SECONDS)) return null
            process.inputStream.readBytes().toString(Charset.defaultCharset()).lineSequence()
                .firstOrNull { "REG_SZ" in it }
                ?.substringAfter("REG_SZ")
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        } catch (_: Exception) {
            null
        } finally {
            process?.takeIf(Process::isAlive)?.destroyForcibly()
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name", "").lowercase(Locale.ROOT).contains("windows")

    private data class CachedExecutable(val path: Path?, val cachedAtNanos: Long) {
        fun isExpired(): Boolean = System.nanoTime() - cachedAtNanos >= EXECUTABLE_CACHE_TTL_NANOS
    }

    private val EXECUTABLE_CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(60)
}
