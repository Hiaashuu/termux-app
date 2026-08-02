package com.termux.shared.activities

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.termux.shared.R
import com.termux.shared.activity.media.AppCompatActivityUtils
import com.termux.shared.data.DataUtils
import com.termux.shared.errors.Error
import com.termux.shared.file.FileUtils
import com.termux.shared.file.filesystem.FileType
import com.termux.shared.interact.ShareUtils
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import com.termux.shared.models.ReportInfo
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.theme.NightMode
import io.noties.markwon.Markwon
import io.noties.markwon.recycler.MarkwonAdapter
import io.noties.markwon.recycler.SimpleEntry
import org.commonmark.node.FencedCodeBlock

/**
 * An activity to show reports in markdown format as per CommonMark spec based on config passed as [ReportInfo].
 * Add Following to `AndroidManifest.xml` to use in an app:
 * `<activity android:name="com.termux.shared.activities.ReportActivity" android:theme="@style/Theme.AppCompat.TermuxReportActivity" android:documentLaunchMode="intoExisting" />`
 * and
 * `<receiver android:name="com.termux.shared.activities.ReportActivity$ReportActivityBroadcastReceiver"  android:exported="false" />`
 * Receiver **must not** be `exported="true"`!!!
 *
 * Also make an incremental call to [deleteReportInfoFilesOlderThanXDays] in the app to cleanup cached files.
 */
class ReportActivity : AppCompatActivity() {

    private var mReportInfo: ReportInfo? = null
    private var mReportInfoFilePath: String? = null
    private var mReportActivityMarkdownString: String = ""
    private var mBundle: Bundle? = null

    private val mTitleState = mutableStateOf("")
    private val mMarkdownState = mutableStateOf("")
    private val mCanSaveState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.logVerbose(LOG_TAG, "onCreate")

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true)

        enableEdgeToEdge()

        mBundle = intent?.extras ?: savedInstanceState

        updateUI()

        setContent {
            ReportTheme {
                ReportScreen(
                    title = mTitleState.value,
                    markdown = mMarkdownState.value,
                    canSave = mCanSaveState.value,
                    onShare = { shareReport() },
                    onCopy = { copyReport() },
                    onSave = { saveReport(REQUEST_GRANT_STORAGE_PERMISSION_FOR_SAVE_FILE) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Logger.logVerbose(LOG_TAG, "onNewIntent")

        setIntent(intent)

        deleteReportInfoFile(this, mReportInfoFilePath)
        mBundle = intent.extras
        updateUI()
    }

    private fun updateUI() {
        val bundle = mBundle
        if (bundle == null) {
            finish(); return
        }

        mReportInfo = null
        mReportInfoFilePath = null

        if (bundle.containsKey(EXTRA_REPORT_INFO_OBJECT_FILE_PATH)) {
            mReportInfoFilePath = bundle.getString(EXTRA_REPORT_INFO_OBJECT_FILE_PATH)
            Logger.logVerbose(LOG_TAG, ReportInfo::class.java.simpleName + " serialized object will be read from file at path \"" + mReportInfoFilePath + "\"")
            if (mReportInfoFilePath != null) {
                try {
                    val result = FileUtils.readSerializableObjectFromFile(ReportInfo::class.java.simpleName, mReportInfoFilePath, ReportInfo::class.java, false)
                    if (result.error != null) {
                        Logger.logErrorExtended(LOG_TAG, result.error.toString())
                        Logger.showToast(this, Error.getMinimalErrorString(result.error), true)
                        finish(); return
                    } else {
                        if (result.serializableObject != null)
                            mReportInfo = result.serializableObject as ReportInfo
                    }
                } catch (e: Exception) {
                    Logger.logErrorAndShowToast(this, LOG_TAG, e.message)
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failure while getting " + ReportInfo::class.java.simpleName + " serialized object from file at path \"" + mReportInfoFilePath + "\"", e)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            mReportInfo = bundle.getSerializable(EXTRA_REPORT_INFO_OBJECT) as? ReportInfo
        }

        val info = mReportInfo
        if (info == null) {
            finish(); return
        }

        mTitleState.value = info.reportTitle ?: (TermuxConstants.TERMUX_APP_NAME + " App Report")
        mCanSaveState.value = info.reportSaveFilePath != null

        generateReportActivityMarkdownString()
        mMarkdownState.value = mReportActivityMarkdownString
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val bundle = mBundle
        if (bundle != null && bundle.containsKey(EXTRA_REPORT_INFO_OBJECT_FILE_PATH)) {
            outState.putString(EXTRA_REPORT_INFO_OBJECT_FILE_PATH, mReportInfoFilePath)
        } else {
            outState.putSerializable(EXTRA_REPORT_INFO_OBJECT, mReportInfo)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.logVerbose(LOG_TAG, "onDestroy")

        deleteReportInfoFile(this, mReportInfoFilePath)
    }

    override fun onBackPressed() {
        // Remove activity from recents menu on back button press
        finishAndRemoveTask()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Logger.logInfo(LOG_TAG, "Storage permission granted by user on request.")
            if (requestCode == REQUEST_GRANT_STORAGE_PERMISSION_FOR_SAVE_FILE) {
                saveReport(-1)
            }
        } else {
            Logger.logInfo(LOG_TAG, "Storage permission denied by user on request.")
        }
    }

    private fun shareReport() {
        val info = mReportInfo ?: return
        ShareUtils.shareText(this, getString(R.string.title_report_text), ReportInfo.getReportInfoMarkdownString(info))
    }

    private fun copyReport() {
        val info = mReportInfo ?: return
        ShareUtils.copyTextToClipboard(this, ReportInfo.getReportInfoMarkdownString(info), null)
    }

    private fun saveReport(requestCode: Int) {
        val info = mReportInfo ?: return
        ShareUtils.saveTextToFile(this, info.reportSaveFileLabel,
            info.reportSaveFilePath, ReportInfo.getReportInfoMarkdownString(info),
            true, requestCode)
    }

    /**
     * Generate the markdown [String] to be shown in [ReportActivity].
     */
    private fun generateReportActivityMarkdownString() {
        // We need to reduce chances of OutOfMemoryError happening so reduce new allocations and
        // do not keep output of getReportInfoMarkdownString in memory
        val info = mReportInfo ?: return
        val reportString = StringBuilder()

        if (info.reportStringPrefix != null)
            reportString.append(info.reportStringPrefix)

        var reportMarkdownString: String? = ReportInfo.getReportInfoMarkdownString(info)
        val reportMarkdownStringSize = reportMarkdownString!!.toByteArray().size
        var truncated = false
        if (reportMarkdownStringSize > ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES) {
            Logger.logVerbose(LOG_TAG, info.reportTitle + " report string size " + reportMarkdownStringSize + " is greater than " + ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES + " and will be truncated")
            reportString.append(DataUtils.getTruncatedCommandOutput(reportMarkdownString, ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES, true, false, true))
            truncated = true
        } else {
            reportString.append(reportMarkdownString)
        }

        // Free reference
        reportMarkdownString = null

        if (info.reportStringSuffix != null)
            reportString.append(info.reportStringSuffix)

        val reportStringSize = reportString.length
        mReportActivityMarkdownString = if (reportStringSize > ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES) {
            // This may break markdown formatting
            Logger.logVerbose(LOG_TAG, info.reportTitle + " report string total size " + reportStringSize + " is greater than " + ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES + " and will be truncated")
            getString(R.string.msg_report_truncated) +
                DataUtils.getTruncatedCommandOutput(reportString.toString(), ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES, true, false, false)
        } else if (truncated) {
            getString(R.string.msg_report_truncated) + reportString.toString()
        } else {
            reportString.toString()
        }
    }

    class NewInstanceResult(
        /** An intent that can be used to start the [ReportActivity]. */
        @JvmField val contentIntent: Intent?,
        /** An intent that can should be adding as the [android.app.Notification.deleteIntent]
         * by a call to [android.app.PendingIntent.getBroadcast] so that
         * [ReportActivityBroadcastReceiver] can do cleanup of [EXTRA_REPORT_INFO_OBJECT_FILE_PATH]. */
        @JvmField val deleteIntent: Intent?
    )

    /**
     * The [BroadcastReceiver] for [ReportActivity] that currently does cleanup when
     * [android.app.Notification.deleteIntent] is called. It must be registered in `AndroidManifest.xml`.
     */
    class ReportActivityBroadcastReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent?) {
            if (intent == null) return

            val action = intent.action
            Logger.logVerbose(RECEIVER_LOG_TAG, "onReceive: \"" + action + "\" action")

            if (ACTION_DELETE_REPORT_INFO_OBJECT_FILE == action) {
                val bundle = intent.extras ?: return
                if (bundle.containsKey(EXTRA_REPORT_INFO_OBJECT_FILE_PATH)) {
                    deleteReportInfoFile(context, bundle.getString(EXTRA_REPORT_INFO_OBJECT_FILE_PATH))
                }
            }
        }

        companion object {
            private const val RECEIVER_LOG_TAG = "ReportActivityBroadcastReceiver"
        }
    }

    companion object {

        private val CLASS_NAME: String = ReportActivity::class.java.canonicalName!!
        private val ACTION_DELETE_REPORT_INFO_OBJECT_FILE = "$CLASS_NAME.ACTION_DELETE_REPORT_INFO_OBJECT_FILE"

        private val EXTRA_REPORT_INFO_OBJECT = "$CLASS_NAME.EXTRA_REPORT_INFO_OBJECT"
        private val EXTRA_REPORT_INFO_OBJECT_FILE_PATH = "$CLASS_NAME.EXTRA_REPORT_INFO_OBJECT_FILE_PATH"

        private const val CACHE_DIR_BASENAME = "report_activity"
        private const val CACHE_FILE_BASENAME_PREFIX = "report_info_"

        const val REQUEST_GRANT_STORAGE_PERMISSION_FOR_SAVE_FILE = 1000

        const val ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES = 1000 * 1024 // 1MB

        private const val LOG_TAG = "ReportActivity"

        /**
         * Start the [ReportActivity].
         *
         * @param context The [Context] for operations.
         * @param reportInfo The [ReportInfo] containing info that needs to be displayed.
         */
        @JvmStatic
        fun startReportActivity(@NonNull context: Context, @NonNull reportInfo: ReportInfo) {
            val result = newInstance(context, reportInfo)
            if (result.contentIntent == null) return
            context.startActivity(result.contentIntent)
        }

        /**
         * Get content and delete intents for the [ReportActivity] that can be used to start it
         * and do cleanup.
         *
         * If [ReportInfo] size is too large, then a TransactionTooLargeException will be thrown
         * so its object may be saved to a file in the [Context.getCacheDir]. Then when activity
         * starts, its read back and the file is deleted in [onDestroy].
         * Note that files may still be left if [onDestroy] is not called or doesn't finish.
         * A separate cleanup routine is implemented from that case by
         * [deleteReportInfoFilesOlderThanXDays] which should be called incrementally or at app startup.
         *
         * @param context The [Context] for operations.
         * @param reportInfo The [ReportInfo] containing info that needs to be displayed.
         * @return Returns [NewInstanceResult].
         */
        @JvmStatic
        @NonNull
        fun newInstance(@NonNull context: Context, @NonNull reportInfo: ReportInfo): NewInstanceResult {
            val size = DataUtils.getSerializedSize(reportInfo)
            if (size > DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES) {
                val reportInfoDirectoryPath = getReportInfoDirectoryPath(context)
                val reportInfoFilePath = "$reportInfoDirectoryPath/$CACHE_FILE_BASENAME_PREFIX${reportInfo.reportTimestamp}"
                Logger.logVerbose(LOG_TAG, reportInfo.reportTitle + " " + ReportInfo::class.java.simpleName + " serialized object size " + size + " is greater than " + DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES + " and it will be written to file at path \"" + reportInfoFilePath + "\"")
                val error = FileUtils.writeSerializableObjectToFile(ReportInfo::class.java.simpleName, reportInfoFilePath, reportInfo)
                if (error != null) {
                    Logger.logErrorExtended(LOG_TAG, error.toString())
                    Logger.showToast(context, Error.getMinimalErrorString(error), true)
                    return NewInstanceResult(null, null)
                }

                return NewInstanceResult(createContentIntent(context, null, reportInfoFilePath),
                    createDeleteIntent(context, reportInfoFilePath))
            } else {
                return NewInstanceResult(createContentIntent(context, reportInfo, null), null)
            }
        }

        private fun createContentIntent(@NonNull context: Context, reportInfo: ReportInfo?, reportInfoFilePath: String?): Intent {
            val intent = Intent(context, ReportActivity::class.java)
            val bundle = Bundle()

            if (reportInfoFilePath != null) {
                bundle.putString(EXTRA_REPORT_INFO_OBJECT_FILE_PATH, reportInfoFilePath)
            } else {
                bundle.putSerializable(EXTRA_REPORT_INFO_OBJECT, reportInfo)
            }

            intent.putExtras(bundle)

            // Note that ReportActivity should have `documentLaunchMode="intoExisting"` set in `AndroidManifest.xml`
            // which has equivalent behaviour to FLAG_ACTIVITY_NEW_DOCUMENT.
            // FLAG_ACTIVITY_SINGLE_TOP must also be passed for onNewIntent to be called.
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            return intent
        }

        private fun createDeleteIntent(@NonNull context: Context, reportInfoFilePath: String?): Intent? {
            if (reportInfoFilePath == null) return null

            val intent = Intent(context, ReportActivityBroadcastReceiver::class.java)
            intent.action = ACTION_DELETE_REPORT_INFO_OBJECT_FILE

            val bundle = Bundle()
            bundle.putString(EXTRA_REPORT_INFO_OBJECT_FILE_PATH, reportInfoFilePath)
            intent.putExtras(bundle)

            return intent
        }

        @NonNull
        private fun getReportInfoDirectoryPath(context: Context): String {
            // Canonicalize to solve /data/data and /data/user/0 issues when comparing with reportInfoFilePath
            return FileUtils.getCanonicalPath(context.cacheDir.absolutePath, null) + "/" + CACHE_DIR_BASENAME
        }

        private fun deleteReportInfoFile(context: Context?, reportInfoFilePathIn: String?) {
            if (context == null || reportInfoFilePathIn == null) return

            // Extra protection for mainly if someone set `exported="true"` for ReportActivityBroadcastReceiver
            val reportInfoDirectoryPath = getReportInfoDirectoryPath(context)
            val reportInfoFilePath = FileUtils.getCanonicalPath(reportInfoFilePathIn, null)
            if (reportInfoFilePath != reportInfoDirectoryPath && reportInfoFilePath.startsWith("$reportInfoDirectoryPath/")) {
                Logger.logVerbose(LOG_TAG, "Deleting " + ReportInfo::class.java.simpleName + " serialized object file at path \"" + reportInfoFilePath + "\"")
                val error = FileUtils.deleteRegularFile(ReportInfo::class.java.simpleName, reportInfoFilePath, true)
                if (error != null) {
                    Logger.logErrorExtended(LOG_TAG, error.toString())
                }
            } else {
                Logger.logError(LOG_TAG, "Not deleting " + ReportInfo::class.java.simpleName + " serialized object file at path \"" + reportInfoFilePath + "\" since its not under \"" + reportInfoDirectoryPath + "\"")
            }
        }

        /**
         * Delete [ReportInfo] serialized object files from cache older than x days. If a notification
         * has still not been opened after x days that's using a PendingIntent to ReportActivity, then
         * opening the notification will throw a file not found error, so choose days value appropriately
         * or check if a notification is still active if tracking notification ids.
         * The [Context] object passed must be of the same package with which [newInstance]
         * was called since a call to [Context.getCacheDir] is made.
         *
         * @param context The [Context] for operations.
         * @param days The x amount of days before which files should be deleted. This must be `>=0`.
         * @param isSynchronous If set to `true`, then the command will be executed in the
         *                      caller thread and results returned synchronously.
         *                      If set to `false`, then a new thread is started run the commands
         *                      asynchronously in the background and control is returned to the caller thread.
         * @return Returns the `error` if deleting was not successful, otherwise `null`.
         */
        @JvmStatic
        fun deleteReportInfoFilesOlderThanXDays(@NonNull context: Context, days: Int, isSynchronous: Boolean): Error? {
            return if (isSynchronous) {
                deleteReportInfoFilesOlderThanXDaysInner(context, days)
            } else {
                Thread {
                    val error = deleteReportInfoFilesOlderThanXDaysInner(context, days)
                    if (error != null) {
                        Logger.logErrorExtended(LOG_TAG, error.toString())
                    }
                }.start()
                null
            }
        }

        private fun deleteReportInfoFilesOlderThanXDaysInner(@NonNull context: Context, days: Int): Error? {
            // Only regular files are deleted and subdirectories are not checked
            val reportInfoDirectoryPath = getReportInfoDirectoryPath(context)
            Logger.logVerbose(LOG_TAG, "Deleting " + ReportInfo::class.java.simpleName + " serialized object files under directory path \"" + reportInfoDirectoryPath + "\" older than " + days + " days")
            return FileUtils.deleteFilesOlderThanXDays(ReportInfo::class.java.simpleName, reportInfoDirectoryPath, null, days, true, FileType.REGULAR.value)
        }
    }
}

@Composable
private fun ReportScreen(
    title: String,
    markdown: String,
    canSave: Boolean,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                ReportTopBar(
                    title = title,
                    canSave = canSave,
                    onShare = onShare,
                    onCopy = onCopy,
                    onSave = onSave
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()) {
                Spacer(modifier = Modifier.height(12.dp))
                ReportCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    ReportMarkdownView(markdown = markdown)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportTopBar(
    title: String,
    canSave: Boolean,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 25.sp,
                maxLines = 1
            )
        },
        actions = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(imageVector = Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(imageVector = Icons.Filled.Share, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        leadingIcon = { Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onCopy()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Save To File") },
                        leadingIcon = { Icon(imageVector = Icons.Filled.Save, contentDescription = null) },
                        enabled = canSave,
                        onClick = {
                            menuExpanded = false
                            onSave()
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        windowInsets = WindowInsets.statusBars
    )
}

@Composable
private fun ReportCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

@Composable
private fun ReportMarkdownView(markdown: String) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        factory = { ctx ->
            val recyclerView = RecyclerView(ctx)
            val markwon = MarkdownUtils.getRecyclerMarkwonBuilder(ctx)
            val adapter = MarkwonAdapter.builderTextViewIsRoot(R.layout.markdown_adapter_node_default)
                .include(FencedCodeBlock::class.java, SimpleEntry.create(R.layout.markdown_adapter_node_code_block, R.id.code_text_view))
                .build()
            recyclerView.layoutManager = LinearLayoutManager(ctx)
            recyclerView.adapter = adapter
            recyclerView.tag = markwon
            recyclerView
        },
        update = { recyclerView ->
            val adapter = recyclerView.adapter as? MarkwonAdapter ?: return@AndroidView
            val markwon = recyclerView.tag as? Markwon ?: return@AndroidView
            adapter.setMarkdown(markwon, markdown)
            adapter.notifyDataSetChanged()
        }
    )
}

private val ReportRedPrimary = Color(0xFFC00021)
private val ReportRedPrimaryDark = Color(0xFFFFB3AE)

private val ReportLightColorScheme = lightColorScheme(
    primary = ReportRedPrimary,
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A19),
    surfaceVariant = Color(0xFFF5DDDB),
    onSurfaceVariant = Color(0xFF534341),
    outlineVariant = Color(0xFFD8C2BF)
)

private val ReportDarkColorScheme = darkColorScheme(
    primary = ReportRedPrimaryDark,
    background = Color(0xFF201A19),
    surface = Color(0xFF201A19),
    onSurface = Color(0xFFEDE0DE),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BF),
    outlineVariant = Color(0xFF534341)
)

@Composable
private fun ReportTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) ReportDarkColorScheme else ReportLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = context as? Activity
            if (activity != null) {
                val window = activity.window
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}