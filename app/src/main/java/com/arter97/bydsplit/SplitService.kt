/*
 * Copyright 2026 Juhyung Park (arter97) <qkrwngud825@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.arter97.bydsplit

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.TaskStackListener
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.util.Log
import android.view.Display
import androidx.core.app.ServiceCompat
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.ShizukuRemoteProcess
import rikka.shizuku.SystemServiceHelper
import java.util.concurrent.Executors

class SplitService : Service() {
    companion object {
        private const val TAG = "bydsplit"
        private const val CHANNEL_ID = "split"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.arter97.bydsplit.STOP"

        const val GAP = 0

        private const val SPLIT_SCREEN_CREATE_MODE_TOP_OR_LEFT = 0
        private const val WINDOWING_MODE_SPLIT_SCREEN_SECONDARY = 4
    }

    // All Shizuku work runs serially off the main thread
    private val executor = Executors.newSingleThreadExecutor()
    private var listening = false
    private var leftPackage: String? = null
    private var rightPackage: String? = null

    private val atm: Any by lazy {
        HiddenApiBypass.addHiddenApiExemptions("")
        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity_task"))
        Class.forName("android.app.IActivityTaskManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!
    }

    private val taskStackListener by lazy {
        HiddenApiBypass.addHiddenApiExemptions("")
        object : TaskStackListener() {
            override fun onTaskStackChanged() {
                executor.execute { checkTopTask() }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_split), NotificationManager.IMPORTANCE_MIN)
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.split_active))
            .addAction(
                Notification.Action.Builder(
                    null, getString(R.string.stop),
                    PendingIntent.getService(
                        this, 0, Intent(this, SplitService::class.java).setAction(ACTION_STOP),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                ).build()
            )
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        executor.execute {
            try {
                split()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to split", e)
                restore()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        executor.execute {
            unregisterListener()
            restore()
        }
        executor.shutdown()
        super.onDestroy()
    }

    private fun split() {
        val prefs = Prefs(this)
        val leftPackage = prefs.leftPackage
        val rightPackage = prefs.rightPackage
        if (leftPackage == null || rightPackage == null) {
            Log.e(TAG, "Apps not configured")
            stopSelf()
            return
        }
        this.leftPackage = leftPackage
        this.rightPackage = rightPackage

        val left = packageManager.getLaunchIntentForPackage(leftPackage)?.component
        val right = packageManager.getLaunchIntentForPackage(rightPackage)?.component
        if (left == null || right == null) {
            Log.e(TAG, "Launch activity not found: $left / $right")
            stopSelf()
            return
        }

        // Ignore stack changes caused by ourselves
        unregisterListener()

        restored = false
        exec("wm overscan 0,-${statusBarHeight()},0,0")
        exec("am start -n ${left.flattenToShortString()}")
        exec("am start -n ${right.flattenToShortString()}")

        // Wait for both tasks to appear
        var leftTask: Int? = null
        var rightTask: Int? = null
        for (i in 0 until 50) {
            val list = exec("am stack list")
            leftTask = taskOf(list, leftPackage)
            rightTask = taskOf(list, rightPackage)
            if (leftTask != null && rightTask != null) break
            Thread.sleep(100)
        }
        if (leftTask == null || rightTask == null) {
            Log.e(TAG, "Task not found: left=$leftTask right=$rightTask")
            stopSelf()
            return
        }

        // Fullscreen stacks can't be resized; move both tasks into split-screen first
        atm.javaClass.getMethod(
            "setTaskWindowingModeSplitScreenPrimary", Int::class.java, Int::class.java,
            Boolean::class.java, Boolean::class.java, Rect::class.java, Boolean::class.java
        ).invoke(atm, leftTask, SPLIT_SCREEN_CREATE_MODE_TOP_OR_LEFT, true, false, null, false)
        atm.javaClass.getMethod(
            "setTaskWindowingMode", Int::class.java, Int::class.java, Boolean::class.java
        ).invoke(atm, rightTask, WINDOWING_MODE_SPLIT_SCREEN_SECONDARY, true)

        val list = exec("am stack list")
        val (leftBounds, rightBounds) = splitBounds()
        exec("am stack resize ${stackOf(list, leftPackage)} $leftBounds")
        exec("am stack resize ${stackOf(list, rightPackage)} $rightBounds")

        registerListener()
    }

    // Target "left top right bottom" of the left and right stacks
    private fun splitBounds(): Pair<String, String> {
        val size = Point()
        @Suppress("DEPRECATION")
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).getRealSize(size)
        val w = maxOf(size.x, size.y)
        val h = minOf(size.x, size.y)
        val x = w * Prefs(this).leftRatio / Prefs.TOTAL_RATIO
        return "0 0 ${x - GAP / 2} $h" to "${x + GAP / 2} 0 $w $h"
    }

    // The OS may re-launch its own split with our apps (e.g. default ratio); re-apply ours
    private fun reapplySplit() {
        val leftPackage = leftPackage ?: return
        val rightPackage = rightPackage ?: return
        val list = exec("am stack list")
        val leftStack = stackOf(list, leftPackage) ?: return
        val rightStack = stackOf(list, rightPackage) ?: return
        if (leftStack == rightStack) return
        val leftCur = boundsOf(list, leftStack) ?: return
        val rightCur = boundsOf(list, rightStack) ?: return
        // Empty bounds mean fullscreen, i.e. not split
        if (!isSplit(list, leftStack) || !isSplit(list, rightStack)) return
        val (leftBounds, rightBounds) = splitBounds()
        if (!restored && leftCur == leftBounds && rightCur == rightBounds) return

        Log.d(TAG, "Split re-detected ($leftCur / $rightCur), re-applying")
        // Ignore stack changes caused by ourselves
        unregisterListener()
        if (restored) {
            restored = false
            exec("wm overscan 0,-${statusBarHeight()},0,0")
        }
        exec("am stack resize $leftStack $leftBounds")
        exec("am stack resize $rightStack $rightBounds")
        registerListener()
    }

    // Overscan exactly the status bar away
    private fun statusBarHeight(): Int {
        @Suppress("DiscouragedApi", "InternalInsetResource")
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        val height = resources.getDimensionPixelSize(id)
        Log.d(TAG, "statusBarHeight: $height")
        return height
    }

    private fun checkTopTask() {
        if (!listening) return
        val top = try {
            @Suppress("UNCHECKED_CAST")
            (atm.javaClass.getMethod("getTasks", Int::class.java).invoke(atm, 1)
                    as List<ActivityManager.RunningTaskInfo>).firstOrNull()?.topActivity?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get top task", e)
            return
        }
        Log.d(TAG, "Top task: $top")
        if (top != null && top != leftPackage && top != rightPackage && top != packageName) {
            // Keep listening so a split the OS re-launches later gets our layout back
            restore()
        } else if (top == leftPackage || top == rightPackage) {
            try {
                reapplySplit()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to re-apply split", e)
            }
        }
    }

    private fun registerListener() {
        if (listening) return
        atm.javaClass.getMethod("registerTaskStackListener", Class.forName("android.app.ITaskStackListener"))
            .invoke(atm, taskStackListener)
        listening = true
    }

    private fun unregisterListener() {
        if (!listening) return
        listening = false
        try {
            atm.javaClass.getMethod("unregisterTaskStackListener", Class.forName("android.app.ITaskStackListener"))
                .invoke(atm, taskStackListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister listener", e)
        }
    }

    // Undo the overscan; called when the user leaves the split apps
    private var restored = false
    private fun restore() {
        if (restored || !Shizuku.pingBinder()) return
        restored = true
        try {
            exec("wm overscan reset")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset overscan", e)
        }
    }

    private fun taskOf(list: String, pkg: String): Int? =
        list.lineSequence().firstOrNull { it.contains("taskId=") && it.contains("$pkg/") }
            ?.substringAfter("taskId=")?.substringBefore(':')?.toIntOrNull()

    private fun stackOf(list: String, pkg: String): String? {
        var id: String? = null
        for (line in list.lineSequence()) {
            if (line.startsWith("Stack id=")) {
                id = line.substringAfter("Stack id=").substringBefore(' ')
            } else if (line.contains("taskId=") && line.contains("$pkg/")) {
                return id
            }
        }
        return null
    }

    // OS-made splits sit in split-screen windowing modes; fullscreen stacks don't
    private fun isSplit(list: String, stack: String): Boolean {
        val lines = list.lines()
        val i = lines.indexOfFirst { it.startsWith("Stack id=$stack ") }
        return i >= 0 && lines.getOrNull(i + 1)?.contains("mWindowingMode=split-screen") == true
    }

    // "Stack id=N bounds=[l,t][r,b] ..." -> "l t r b"
    private fun boundsOf(list: String, stack: String): String? =
        list.lineSequence().firstOrNull { it.startsWith("Stack id=$stack ") }
            ?.substringAfter("bounds=[", "")?.substringBefore(" ")
            ?.replace("][", ",")?.trim('[', ']')?.split(',')
            ?.takeIf { it.size == 4 }?.joinToString(" ")

    private fun exec(cmd: String): String {
        // Shizuku.newProcess() is private since API 13; call it via reflection
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
        val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as ShizukuRemoteProcess
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor()
        Log.d(TAG, "$ $cmd\n$out")
        return out
    }
}
