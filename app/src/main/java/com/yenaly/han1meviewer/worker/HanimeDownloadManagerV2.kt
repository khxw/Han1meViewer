package com.yenaly.han1meviewer.worker

import android.util.Log
import androidx.lifecycle.Observer
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.dao.DownloadDatabase
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.state.DownloadState
import com.yenaly.han1meviewer.util.runSuspendCatching
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Serializes all scheduler state through a single channel actor.
 *
 * Download workers only report completion back to the actor; they never mutate the
 * active/waiting collections directly. This keeps concurrency accounting thread-safe.
 */
object HanimeDownloadManagerV2 {

    private const val TAG = "HanimeDownloadManager"

    const val MAX_CONCURRENT_DOWNLOAD_DEF = 2

    private val workManager = WorkManager.getInstance(applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val activeDownloads = linkedMapOf<String, HanimeDownloadWorker.Args>()
    private val waitingQueue = ArrayDeque<HanimeDownloadWorker.Args>()
    private val stoppedForRequeue = linkedMapOf<String, HanimeDownloadWorker.Args>()
    private val pauseRequested = mutableSetOf<String>()
    private val resumeAfterStop = mutableSetOf<String>()
    private val downloadChannel = Channel<DownloadMsg>(capacity = Channel.UNLIMITED)

    private var refreshPending = false

    @Volatile
    var maxConcurrentDownloadCount: Int = MAX_CONCURRENT_DOWNLOAD_DEF
        private set

    init {
        maxConcurrentDownloadCount = normalizeConcurrentLimit(Preferences.downloadCountLimit)
        scope.launch {
            for (message in downloadChannel) {
                handleMessage(message)
            }
        }
    }

    private sealed class DownloadMsg {
        data class Add(
            val args: HanimeDownloadWorker.Args,
            val redownload: Boolean = false,
            val waiting: Boolean = false,
            val state: DownloadState = DownloadState.Unknown,
        ) : DownloadMsg()

        data class Resume(val args: HanimeDownloadWorker.Args) : DownloadMsg()
        data class Stop(val args: HanimeDownloadWorker.Args) : DownloadMsg()
        data class Delete(val args: HanimeDownloadWorker.Args) : DownloadMsg()
        data class Finished(val videoCode: String) : DownloadMsg()
        data object Reconfigure : DownloadMsg()
        data object RefreshNetworkConstraints : DownloadMsg()
    }

    private suspend fun handleMessage(message: DownloadMsg) {
        when (message) {
            is DownloadMsg.Add -> handleAdd(message)
            is DownloadMsg.Resume -> handleResume(message.args)
            is DownloadMsg.Stop -> handleStop(message.args)
            is DownloadMsg.Delete -> handleDelete(message.args)
            is DownloadMsg.Finished -> handleFinished(message.videoCode)
            DownloadMsg.Reconfigure -> handleReconfigure()
            DownloadMsg.RefreshNetworkConstraints -> handleNetworkConstraintRefresh()
        }
    }

    private suspend fun handleAdd(message: DownloadMsg.Add) {
        val args = message.args
        if (args.videoCode in activeDownloads ||
            waitingQueue.any { it.videoCode == args.videoCode } ||
            args.videoCode in stoppedForRequeue
        ) {
            Log.d(TAG, "任务已存在：${args.videoCode}")
            return
        }

        if (activeDownloads.size < maxConcurrentDownloadCount &&
            (message.state == DownloadState.Downloading || message.state == DownloadState.Unknown)
        ) {
            Log.d(TAG, "开始下载：${args.videoCode}")
            activeDownloads[args.videoCode] = args
            launchDownload(args, message.redownload, message.waiting)
            return
        }

        when (message.state) {
            DownloadState.Downloading -> {
                waitingQueue.addFirst(args)
                enqueueWaitingWork(args, message.redownload)
            }

            DownloadState.Queued, DownloadState.Unknown -> {
                waitingQueue.addLast(args)
                enqueueWaitingWork(args, message.redownload)
            }

            else -> Unit
        }
    }

    private suspend fun handleResume(args: HanimeDownloadWorker.Args) {
        waitingQueue.removeIf { it.videoCode == args.videoCode }
        if (args.videoCode in activeDownloads) {
            if (args.videoCode in pauseRequested) {
                resumeAfterStop.add(args.videoCode)
                markQueued(args)
                Log.d(TAG, "记录暂停中的恢复请求：${args.videoCode}")
            } else {
                Log.d(TAG, "任务已处于活动状态：${args.videoCode}")
            }
            return
        }
        if (args.videoCode in stoppedForRequeue) {
            resumeAfterStop.add(args.videoCode)
            markQueued(args)
            Log.d(TAG, "记录停止中的恢复请求：${args.videoCode}")
            return
        }

        if (activeDownloads.size < maxConcurrentDownloadCount) {
            Log.d(TAG, "恢复下载：${args.videoCode}")
            activeDownloads[args.videoCode] = args
            launchDownload(args, redownload = false, waiting = false)
        } else {
            waitingQueue.addFirst(args)
            markQueued(args)
        }
    }

    private suspend fun handleStop(args: HanimeDownloadWorker.Args) {
        if (args.videoCode in activeDownloads) {
            Log.d(TAG, "停止任务：${args.videoCode}")
            pauseRequested.add(args.videoCode)
            resumeAfterStop.remove(args.videoCode)
            // Reflect the action immediately while WorkManager finishes cancellation.
            markPaused(args)
            cancelWorkAsync(args)
            return
        }

        waitingQueue.removeIf { it.videoCode == args.videoCode }
        stoppedForRequeue.remove(args.videoCode)
        pauseRequested.remove(args.videoCode)
        resumeAfterStop.remove(args.videoCode)
        markPaused(args)
    }

    private suspend fun handleDelete(args: HanimeDownloadWorker.Args) {
        waitingQueue.removeIf { it.videoCode == args.videoCode }
        stoppedForRequeue.remove(args.videoCode)
        pauseRequested.remove(args.videoCode)
        resumeAfterStop.remove(args.videoCode)
        val wasActive = activeDownloads.remove(args.videoCode) != null
        if (wasActive) stopWork(args)
        deleteWork(args)
        if (wasActive) processNext()
    }

    private suspend fun handleFinished(videoCode: String) {
        val activeArgs = activeDownloads.remove(videoCode)
        val wasActive = activeArgs != null
        pauseRequested.remove(videoCode)
        val shouldResume = resumeAfterStop.remove(videoCode)
        val requeue = stoppedForRequeue.remove(videoCode)
        if (shouldResume) {
            val resumeArgs = requeue ?: activeArgs
            if (resumeArgs != null) {
                waitingQueue.removeIf { it.videoCode == videoCode }
                waitingQueue.addFirst(resumeArgs)
                markQueued(resumeArgs)
            }
        } else if (requeue != null) {
            waitingQueue.removeIf { it.videoCode == videoCode }
            waitingQueue.addLast(requeue)
            markQueued(requeue)
        }

        if (refreshPending) {
            if (stoppedForRequeue.isEmpty()) {
                refreshPending = false
                processNext()
            }
        } else if (wasActive || requeue != null) {
            processNext()
        }
    }

    private suspend fun handleReconfigure() {
        while (activeDownloads.size > maxConcurrentDownloadCount) {
            val (videoCode, args) = activeDownloads.entries.first()
            activeDownloads.remove(videoCode)
            stoppedForRequeue[videoCode] = args
            stopWork(args)
            Log.d(TAG, "并发限制降低，暂停并重新排队：$videoCode")
        }
        processNext()
    }

    private suspend fun handleNetworkConstraintRefresh() {
        if (activeDownloads.isEmpty()) {
            processNext()
            return
        }

        refreshPending = true
        val tasks = activeDownloads.entries.toList()
        activeDownloads.clear()
        tasks.forEach { (videoCode, args) ->
            stoppedForRequeue[videoCode] = args
            stopWork(args)
        }
    }

    suspend fun init() {
        Log.d(TAG, "init")
        DownloadDatabase.instance.hanimeDownloadDao
            .loadAllDownloadingHanimeOnce()
            .forEach { entity ->
                downloadChannel.send(
                    DownloadMsg.Add(
                        args = HanimeDownloadWorker.Args.fromEntity(entity),
                        state = entity.state,
                    )
                )
            }
    }

    fun addTask(
        args: HanimeDownloadWorker.Args,
        redownload: Boolean = false,
        waiting: Boolean = false,
    ) {
        scope.launch {
            downloadChannel.send(DownloadMsg.Add(args, redownload, waiting))
        }
    }

    fun resumeTask(entity: HanimeDownloadEntity) {
        val args = HanimeDownloadWorker.Args.fromEntity(entity)
        scope.launch { downloadChannel.send(DownloadMsg.Resume(args)) }
    }

    fun stopTask(entity: HanimeDownloadEntity) {
        val args = HanimeDownloadWorker.Args.fromEntity(entity)
        scope.launch { downloadChannel.send(DownloadMsg.Stop(args)) }
    }

    fun deleteTask(entity: HanimeDownloadEntity) {
        val args = HanimeDownloadWorker.Args.fromEntity(entity)
        scope.launch { downloadChannel.send(DownloadMsg.Delete(args)) }
    }

    fun updateConcurrentDownloadLimit(value: Int) {
        val normalized = normalizeConcurrentLimit(value)
        if (maxConcurrentDownloadCount == normalized) return
        maxConcurrentDownloadCount = normalized
        scope.launch { downloadChannel.send(DownloadMsg.Reconfigure) }
    }

    fun refreshNetworkConstraints() {
        scope.launch { downloadChannel.send(DownloadMsg.RefreshNetworkConstraints) }
    }

    private fun normalizeConcurrentLimit(value: Int): Int =
        if (value > 0) value else Int.MAX_VALUE

    private fun processNext() {
        while (activeDownloads.size < maxConcurrentDownloadCount && waitingQueue.isNotEmpty()) {
            val next = waitingQueue.removeFirst()
            if (next.videoCode in activeDownloads || next.videoCode in stoppedForRequeue) {
                continue
            }
            activeDownloads[next.videoCode] = next
            launchDownload(next, redownload = false, waiting = false)
        }
    }

    private fun launchDownload(
        args: HanimeDownloadWorker.Args,
        redownload: Boolean,
        waiting: Boolean,
    ) {
        scope.launch {
            try {
                if (waiting) {
                    markQueued(args)
                } else {
                    val workId = startWork(args, redownload)
                    awaitWorkCompletion(args.videoCode, workId.toString())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "启动或等待下载任务失败：${args.videoCode}", t)
                markPaused(args)
            } finally {
                downloadChannel.send(DownloadMsg.Finished(args.videoCode))
            }
        }
    }

    private fun cancelWorkAsync(args: HanimeDownloadWorker.Args) {
        scope.launch {
            runSuspendCatching {
                workManager.cancelUniqueWork(args.videoCode).await()
                Log.d(TAG, "cancelUniqueWork 已提交：${args.videoCode}")
            }.onFailure { throwable ->
                Log.e(TAG, "取消下载任务失败：${args.videoCode}", throwable)
            }
        }
    }

    private suspend fun startWork(
        args: HanimeDownloadWorker.Args,
        redownload: Boolean = false,
        waiting: Boolean = false,
        delete: Boolean = false,
    ) = HanimeDownloadWorker.build(constraintsRequired = !delete) {
        setInputData(
            workDataOf(
                HanimeDownloadWorker.QUALITY to args.quality,
                HanimeDownloadWorker.DOWNLOAD_URL to args.downloadUrl,
                HanimeDownloadWorker.VIDEO_TYPE to args.videoType,
                HanimeDownloadWorker.HANIME_NAME to args.hanimeName,
                HanimeDownloadWorker.VIDEO_CODE to args.videoCode,
                HanimeDownloadWorker.COVER_URL to args.coverUrl,
                HanimeDownloadWorker.REDOWNLOAD to redownload,
                HanimeDownloadWorker.IN_WAITING_QUEUE to waiting,
                HanimeDownloadWorker.DELETE to delete,
            )
        )
    }.apply {
        workManager.beginUniqueWork(
            args.videoCode,
            ExistingWorkPolicy.REPLACE,
            this,
        ).enqueue().await()
    }.id

    private suspend fun stopWork(args: HanimeDownloadWorker.Args) {
        runSuspendCatching {
            workManager.cancelUniqueWork(args.videoCode).await()
            markPaused(args)
            Log.d(TAG, "stopWork (cancelUniqueWork): ${args.videoCode}")
        }.onFailure { throwable ->
            throwable.printStackTrace()
            markPaused(args)
        }
    }

    private suspend fun markQueued(args: HanimeDownloadWorker.Args) {
        DatabaseRepo.HanimeDownload.find(args.videoCode, args.quality.orEmpty())?.let { entity ->
            DatabaseRepo.HanimeDownload.update(entity.copy(state = DownloadState.Queued))
        }
    }

    private suspend fun enqueueWaitingWork(
        args: HanimeDownloadWorker.Args,
        redownload: Boolean = false,
    ) {
        val entity = DatabaseRepo.HanimeDownload.find(args.videoCode, args.quality.orEmpty())
        if (entity == null) {
            startWork(args, redownload = redownload, waiting = true)
        } else {
            DatabaseRepo.HanimeDownload.update(entity.copy(state = DownloadState.Queued))
        }
    }

    private suspend fun markPaused(args: HanimeDownloadWorker.Args) {
        DatabaseRepo.HanimeDownload.find(args.videoCode, args.quality.orEmpty())?.let { entity ->
            if (entity.state != DownloadState.Finished) {
                DatabaseRepo.HanimeDownload.update(entity.copy(state = DownloadState.Paused))
            }
        }
    }

    private suspend fun deleteWork(args: HanimeDownloadWorker.Args) =
        startWork(args, delete = true)

    private suspend fun awaitWorkCompletion(videoCode: String, workId: String) =
        suspendCancellableCoroutine { continuation ->
            val liveData = workManager.getWorkInfosForUniqueWorkLiveData(videoCode)
            Log.d(TAG, "获取 LiveData：$videoCode")
            val observer = object : Observer<List<WorkInfo>> {
                override fun onChanged(value: List<WorkInfo>) {
                    val info = value.firstOrNull { it.id.toString() == workId } ?: return
                    if (info.state.isFinished && continuation.isActive) {
                        Log.d(TAG, "任务完成，移除 observer：$videoCode")
                        liveData.removeObserver(this)
                        continuation.resume(Unit)
                    }
                }
            }
            mainScope.launch {
                if (continuation.isActive) {
                    liveData.observeForever(observer)
                    Log.d(TAG, "添加 observer：$videoCode")
                } else {
                    liveData.removeObserver(observer)
                }
            }
            continuation.invokeOnCancellation {
                mainScope.launch { liveData.removeObserver(observer) }
            }
        }
}
