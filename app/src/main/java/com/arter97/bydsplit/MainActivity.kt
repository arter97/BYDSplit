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

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_CODE = 97
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val EXTRA_SETTINGS = "settings"
    }

    private class AppEntry(val packageName: String, val label: String, val icon: Drawable)

    private lateinit var prefs: Prefs

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, result ->
        if (requestCode != REQUEST_CODE) return@OnRequestPermissionResultListener
        if (result == PackageManager.PERMISSION_GRANTED) {
            startSplit()
        } else {
            Toast.makeText(this, R.string.shizuku_permission_denied, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        if (prefs.isConfigured && !intent.getBooleanExtra(EXTRA_SETTINGS, false)) {
            checkShizukuAndSplit()
            return
        }

        val apps = loadApps()
        setContent {
            Box(modifier = Modifier.fillMaxSize().background(Colors.surface)) {
                SettingsScreen(apps)
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun loadApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(packageManager).toString(), it.loadIcon(packageManager)) }
            .sortedBy { it.label.lowercase() }
    }

    private fun checkShizukuAndSplit() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, R.string.shizuku_not_running, Toast.LENGTH_SHORT).show()
            packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?.let { startActivity(it) }
            finish()
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            startSplit()
        } else {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    private fun startSplit() {
        startForegroundService(Intent(this, SplitService::class.java))
        finish()
    }

    @Composable
    private fun SettingsScreen(apps: List<AppEntry>) {
        var leftPackage by remember { mutableStateOf(prefs.leftPackage) }
        var rightPackage by remember { mutableStateOf(prefs.rightPackage) }
        var leftRatio by remember { mutableIntStateOf(prefs.leftRatio) }
        var picking by remember { mutableStateOf<Boolean?>(null) } // true: left, false: right

        // targetSdk 35+ is always edge-to-edge; keep clear of system bars and cutouts
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                SplitPreview(
                    leftApp = apps.find { it.packageName == leftPackage },
                    rightApp = apps.find { it.packageName == rightPackage },
                    leftRatio = leftRatio,
                    onRatioChange = { leftRatio = it },
                    onLeftClick = { picking = true },
                    onRightClick = { picking = false },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$leftRatio : ${Prefs.TOTAL_RATIO - leftRatio}",
                        fontSize = 22.sp,
                        modifier = Modifier.weight(1f)
                    )
                    PillButton(onClick = {
                        if (leftPackage == null || rightPackage == null) {
                            Toast.makeText(this@MainActivity, R.string.select_both_apps, Toast.LENGTH_SHORT).show()
                            return@PillButton
                        }
                        prefs.leftPackage = leftPackage
                        prefs.rightPackage = rightPackage
                        prefs.leftRatio = leftRatio
                        Toast.makeText(this@MainActivity, R.string.reinstall_to_reconfigure, Toast.LENGTH_LONG).show()
                        checkShizukuAndSplit()
                    }, text = stringResource(R.string.apply))
                }
            }
        }

        picking?.let { left ->
            AppPicker(
                apps = apps,
                onPick = {
                    if (left) leftPackage = it else rightPackage = it
                    picking = null
                },
                onDismiss = { picking = null }
            )
        }
    }

    @Composable
    private fun SplitPreview(
        leftApp: AppEntry?,
        rightApp: AppEntry?,
        leftRatio: Int,
        onRatioChange: (Int) -> Unit,
        onLeftClick: () -> Unit,
        onRightClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        var width by remember { mutableIntStateOf(1) }
        var dragX by remember { mutableFloatStateOf(0f) }
        val currentRatio by rememberUpdatedState(leftRatio)

        Row(modifier = modifier.onSizeChanged { width = it.width }) {
            AppBox(leftApp, onLeftClick, Modifier.weight(leftRatio.toFloat()).fillMaxHeight())
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragX = width.toFloat() * currentRatio / Prefs.TOTAL_RATIO },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                dragX += amount
                                val ratio = (dragX / width * Prefs.TOTAL_RATIO).roundToInt()
                                    .coerceIn(Prefs.MIN_RATIO, Prefs.MAX_RATIO)
                                onRatioChange(ratio)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Colors.onSurfaceVariant)
                )
            }
            AppBox(rightApp, onRightClick, Modifier.weight((Prefs.TOTAL_RATIO - leftRatio).toFloat()).fillMaxHeight())
        }
    }

    @Composable
    private fun AppBox(app: AppEntry?, onClick: () -> Unit, modifier: Modifier) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Colors.surfaceVariant)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (app == null) {
                Text(stringResource(R.string.no_app_selected), textAlign = TextAlign.Center, fontSize = 20.sp)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(app, 96)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(app.label, textAlign = TextAlign.Center, fontSize = 20.sp)
                }
            }
        }
    }

    @Composable
    private fun AppIcon(app: AppEntry, sizeDp: Int) {
        val bitmap = remember(app.packageName) { app.icon.toBitmap().asImageBitmap() }
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(sizeDp.dp))
    }

    @Composable
    private fun AppPicker(apps: List<AppEntry>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Colors.dialog)
                    .padding(24.dp)
            ) {
                Text(stringResource(R.string.choose_app), fontSize = 24.sp)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(apps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app.packageName) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            AppIcon(app, 40)
                            Text(app.label, color = Colors.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.cancel), color = Colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    @Composable
    private fun PillButton(onClick: () -> Unit, text: String) {
        Box(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Colors.primary)
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Colors.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }

    @Composable
    private fun Text(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Colors.onSurface,
        fontSize: TextUnit = 16.sp,
        fontWeight: FontWeight? = null,
        textAlign: TextAlign = TextAlign.Unspecified
    ) {
        BasicText(
            text = text,
            modifier = modifier,
            style = TextStyle(color = color, fontSize = fontSize, fontWeight = fontWeight, textAlign = textAlign)
        )
    }

    // Material 3 baseline dark colors, used without pulling in material3
    private object Colors {
        val surface = Color(0xFF141218)
        val onSurface = Color(0xFFE6E0E9)
        val surfaceVariant = Color(0xFF49454F)
        val onSurfaceVariant = Color(0xFFCAC4D0)
        val dialog = Color(0xFF2B2930)
        val primary = Color(0xFFD0BCFF)
        val onPrimary = Color(0xFF381E72)
    }
}
