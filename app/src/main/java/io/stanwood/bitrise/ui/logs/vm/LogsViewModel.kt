/*
 * Copyright (c) 2018 stanwood Gmbh
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.stanwood.bitrise.ui.logs.vm

import android.text.Spannable
import androidx.databinding.BaseObservable
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.navigation.NavController
import io.stanwood.bitrise.R
import io.stanwood.bitrise.data.model.App
import io.stanwood.bitrise.data.model.Build
import io.stanwood.bitrise.data.net.BitriseService
import io.stanwood.bitrise.di.Properties
import io.stanwood.bitrise.util.extensions.ansiEscapeToSpannable
import io.stanwood.bitrise.util.extensions.bundleOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class LogsViewModel(
        private val service: BitriseService,
        private val token: String,
        private val router: NavController,
        private val app: App,
        private val build: Build,
        private val mainScope: CoroutineScope
) : BaseObservable() {

    val isLoading = ObservableBoolean(false)
    var showLog = ObservableBoolean(false)
    var log = ObservableField<Spannable>()

    fun onRefresh() {
        showLog.set(true)
        mainScope.launch {
            try {
                isLoading.set(true)
                log.apply {
                    set(fetchLog().ansiEscapeToSpannable())
                }
            } catch (exception: CancellationException) {
                /* noop */
            } catch (exception: Exception) {
                Timber.e(exception)
                bundleOf(Properties.MESSAGE to exception.message).apply {
                    router.navigate(R.id.action_error, this)
                }
            } finally {
                isLoading.set(false)
            }
        }
    }

    private suspend fun fetchLog() =
            service
                    .getBuildLog(token, app.slug, build.slug)
                    .await()
                    .let {
                        if (it.isArchived) {
                            service.downloadFile(it.expiringRawLogUrl)
                                    .await()
                                    .charStream()
                                    .readText()
                        } else {
                            it.logChunks
                                    .asSequence()
                                    .sortedBy { logChunk -> logChunk.position }
                                    .joinToString { logChunk -> logChunk.chunk }
                        }
                    }
}