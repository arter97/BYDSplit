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

import android.content.Context
import androidx.core.content.edit

class Prefs(context: Context) {
    companion object {
        const val MIN_RATIO = 2
        const val MAX_RATIO = 8
        const val TOTAL_RATIO = 10
        const val DEFAULT_LEFT_RATIO = 7

        private const val KEY_LEFT_PACKAGE = "left_package"
        private const val KEY_RIGHT_PACKAGE = "right_package"
        private const val KEY_LEFT_RATIO = "left_ratio"
    }

    private val prefs = context.getSharedPreferences("split", Context.MODE_PRIVATE)

    var leftPackage: String?
        get() = prefs.getString(KEY_LEFT_PACKAGE, null)
        set(value) = prefs.edit { putString(KEY_LEFT_PACKAGE, value) }

    var rightPackage: String?
        get() = prefs.getString(KEY_RIGHT_PACKAGE, null)
        set(value) = prefs.edit { putString(KEY_RIGHT_PACKAGE, value) }

    // Left side's share out of TOTAL_RATIO
    var leftRatio: Int
        get() = prefs.getInt(KEY_LEFT_RATIO, DEFAULT_LEFT_RATIO).coerceIn(MIN_RATIO, MAX_RATIO)
        set(value) = prefs.edit { putInt(KEY_LEFT_RATIO, value) }

    val isConfigured: Boolean
        get() = leftPackage != null && rightPackage != null
}
