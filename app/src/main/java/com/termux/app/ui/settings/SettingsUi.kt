package com.termux.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.app.activities.LegacyPluginPreferencesActivity
import com.termux.app.models.UserAction
import com.termux.app.ui.theme.TermuxSettingsTheme
import com.termux.shared.activities.ReportActivity
import com.termux.shared.android.AndroidUtils
import com.termux.shared.android.PackageUtils
import com.termux.shared.file.FileUtils
import com.termux.shared.interact.ShareUtils
import com.termux.shared.logger.Logger
import com.termux.shared.models.ReportInfo
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxTaskerAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxWidgetAppSharedPreferences
import com.termux.shared.theme.NightMode
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsRoute {
    ROOT,
    TERMUX,
    DEBUGGING,
    TERMINAL_IO,
    TERMINAL_VIEW
}

private enum class ThemeMode(val storedValue: String) {
    AUTO(NightMode.SYSTEM.getName()),
    LIGHT(NightMode.FALSE.getName()),
    DARK(NightMode.TRUE.getName());

    companion object {
        fun fromStoredValue(value: String?): ThemeMode {
            for (mode in values()) {
                if (mode.storedValue == value) {
                    return mode
                }
            }
            return AUTO
        }
    }
}

@Composable
fun SettingsApp(onFinish: () -> Unit) {
    val context = LocalContext.current

    var themeMode by remember { mutableStateOf(ThemeMode.AUTO) }

    LaunchedEffect(Unit) {
        val preferences = withContext(Dispatchers.IO) {
            TermuxAppSharedPreferences.build(context, false)
        }
        if (preferences != null) {
            themeMode = ThemeMode.fromStoredValue(preferences.getNightMode())
        }
    }

    val darkTheme = when (themeMode) {
        ThemeMode.AUTO -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    TermuxSettingsTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val backStack = remember { mutableStateListOf(SettingsRoute.ROOT) }

            BackHandler(enabled = true) {
                if (backStack.size > 1) {
                    backStack.removeAt(backStack.size - 1)
                } else {
                    onFinish()
                }
            }

            fun navigateTo(route: SettingsRoute) {
                backStack.add(route)
            }

            fun navigateBack() {
                if (backStack.size > 1) {
                    backStack.removeAt(backStack.size - 1)
                } else {
                    onFinish()
                }
            }

            fun onThemeModeChange(mode: ThemeMode) {
                themeMode = mode
                val preferences = TermuxAppSharedPreferences.build(context, false)
                if (preferences != null) {
                    preferences.setNightMode(mode.storedValue)
                }
            }

            when (backStack.last()) {
                SettingsRoute.ROOT -> {
                    RootSettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChange = { mode -> onThemeModeChange(mode) },
                        onBack = { navigateBack() },
                        onNavigateToTermux = { navigateTo(SettingsRoute.TERMUX) }
                    )
                }
                SettingsRoute.TERMUX -> {
                    TermuxSettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChange = { mode -> onThemeModeChange(mode) },
                        onBack = { navigateBack() },
                        onNavigateToDebugging = { navigateTo(SettingsRoute.DEBUGGING) },
                        onNavigateToTerminalIO = { navigateTo(SettingsRoute.TERMINAL_IO) },
                        onNavigateToTerminalView = { navigateTo(SettingsRoute.TERMINAL_VIEW) }
                    )
                }
                SettingsRoute.DEBUGGING -> {
                    DebuggingSettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChange = { mode -> onThemeModeChange(mode) },
                        onBack = { navigateBack() }
                    )
                }
                SettingsRoute.TERMINAL_IO -> {
                    TerminalIOSettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChange = { mode -> onThemeModeChange(mode) },
                        onBack = { navigateBack() }
                    )
                }
                SettingsRoute.TERMINAL_VIEW -> {
                    TerminalViewSettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChange = { mode -> onThemeModeChange(mode) },
                        onBack = { navigateBack() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 25.sp
            )
        },
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                }
            }
        },
        actions = {
            ThemeModeToggle(
                selected = themeMode,
                onSelect = onThemeModeChange
            )
            Spacer(modifier = Modifier.width(8.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        windowInsets = WindowInsets.statusBars
    )
}

@Composable
private fun ThemeModeToggle(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemeModeToggleOption(
            icon = Icons.Filled.BrightnessAuto,
            isSelected = selected == ThemeMode.AUTO,
            onClick = { onSelect(ThemeMode.AUTO) }
        )
        ThemeModeToggleOption(
            icon = Icons.Filled.LightMode,
            isSelected = selected == ThemeMode.LIGHT,
            onClick = { onSelect(ThemeMode.LIGHT) }
        )
        ThemeModeToggleOption(
            icon = Icons.Filled.DarkMode,
            isSelected = selected == ThemeMode.DARK,
            onClick = { onSelect(ThemeMode.DARK) }
        )
    }
}

@Composable
private fun ThemeModeToggleOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }
    val tintColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SettingsGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontWeight = FontWeight.Light,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
    }
}

@Composable
private fun SettingsClickableRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    showDivider: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontWeight = FontWeight.Light,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
    }
}

@Composable
private fun LogLevelDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val context = LocalContext.current
    val logLevels = Logger.getLogLevelsArray()
    val logLevelLabels = Logger.getLogLevelLabelsArray(context, logLevels, true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(id = R.string.termux_log_level_title))
        },
        text = {
            Column {
                for (index in logLevels.indices) {
                    val value = logLevels[index].toString().toInt()
                    val label = logLevelLabels[index].toString()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == currentValue,
                            onClick = {
                                onSelect(value)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun AnimatedLoadingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(56.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun RootSettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onNavigateToTermux: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var termuxApiVisible by remember { mutableStateOf(false) }
    var termuxFloatVisible by remember { mutableStateOf(false) }
    var termuxTaskerVisible by remember { mutableStateOf(false) }
    var termuxWidgetVisible by remember { mutableStateOf(false) }
    var donateVisible by remember { mutableStateOf(false) }
    var isGeneratingAbout by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            termuxApiVisible = TermuxAPIAppSharedPreferences.build(context, false) != null
            termuxFloatVisible = TermuxFloatAppSharedPreferences.build(context, false) != null
            termuxTaskerVisible = TermuxTaskerAppSharedPreferences.build(context, false) != null
            termuxWidgetVisible = TermuxWidgetAppSharedPreferences.build(context, false) != null

            val digest = PackageUtils.getSigningCertificateSHA256DigestForPackage(context)
            if (digest != null) {
                val apkRelease = TermuxUtils.getAPKRelease(digest)
                donateVisible = !(apkRelease == null || apkRelease == TermuxConstants.APK_RELEASE_GOOGLE_PLAYSTORE_SIGNING_CERTIFICATE_SHA256_DIGEST)
            }
        }
    }

    fun openAbout() {
        isGeneratingAbout = true
        scope.launch {
            withContext(Dispatchers.Default) {
                val title = "About"

                val aboutString = StringBuilder()
                aboutString.append(TermuxUtils.getAppInfoMarkdownString(context, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES))
                aboutString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(context, true))
                aboutString.append("\n\n").append(TermuxUtils.getImportantLinksMarkdownString(context))

                val userActionName = UserAction.ABOUT.getName()

                val reportInfo = ReportInfo(
                    userActionName,
                    TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME,
                    title
                )
                reportInfo.setReportString(aboutString.toString())
                reportInfo.setReportSaveFileLabelAndPath(
                    userActionName,
                    Environment.getExternalStorageDirectory().toString() + "/" +
                        FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true)
                )

                withContext(Dispatchers.Main) {
                    isGeneratingAbout = false
                    ReportActivity.startReportActivity(context, reportInfo)
                }
            }
        }
    }

    fun openDonate() {
        ShareUtils.openUrl(context, TermuxConstants.TERMUX_DONATE_URL)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                SettingsTopBar(
                    title = stringResource(id = R.string.title_activity_termux_settings),
                    showBack = true,
                    onBack = onBack,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                Spacer(modifier = Modifier.height(12.dp))
                SettingsCard {
                    SettingsClickableRow(
                        title = stringResource(id = R.string.termux_preferences_title),
                        subtitle = stringResource(id = R.string.termux_preferences_summary),
                        onClick = onNavigateToTermux,
                        showDivider = termuxApiVisible || termuxFloatVisible || termuxTaskerVisible || termuxWidgetVisible
                    )
                    if (termuxApiVisible) {
                        SettingsClickableRow(
                            title = stringResource(id = R.string.termux_api_preferences_title),
                            subtitle = stringResource(id = R.string.termux_api_preferences_summary),
                            onClick = { LegacyPluginPreferencesActivity.start(context, "termux_api") },
                            showDivider = termuxFloatVisible || termuxTaskerVisible || termuxWidgetVisible
                        )
                    }
                    if (termuxFloatVisible) {
                        SettingsClickableRow(
                            title = stringResource(id = R.string.termux_float_preferences_title),
                            subtitle = stringResource(id = R.string.termux_float_preferences_summary),
                            onClick = { LegacyPluginPreferencesActivity.start(context, "termux_float") },
                            showDivider = termuxTaskerVisible || termuxWidgetVisible
                        )
                    }
                    if (termuxTaskerVisible) {
                        SettingsClickableRow(
                            title = stringResource(id = R.string.termux_tasker_preferences_title),
                            subtitle = stringResource(id = R.string.termux_tasker_preferences_summary),
                            onClick = { LegacyPluginPreferencesActivity.start(context, "termux_tasker") },
                            showDivider = termuxWidgetVisible
                        )
                    }
                    if (termuxWidgetVisible) {
                        SettingsClickableRow(
                            title = stringResource(id = R.string.termux_widget_preferences_title),
                            subtitle = stringResource(id = R.string.termux_widget_preferences_summary),
                            onClick = { LegacyPluginPreferencesActivity.start(context, "termux_widget") },
                            showDivider = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard {
                    SettingsClickableRow(
                        title = stringResource(id = R.string.about_preference_title),
                        subtitle = null,
                        onClick = { openAbout() },
                        showDivider = donateVisible
                    )
                    if (donateVisible) {
                        SettingsClickableRow(
                            title = stringResource(id = R.string.donate_preference_title),
                            subtitle = null,
                            onClick = { openDonate() },
                            showDivider = false
                        )
                    }
                }
            }
        }

        if (isGeneratingAbout) {
            AnimatedLoadingOverlay()
        }
    }
}

@Composable
private fun TermuxSettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onNavigateToDebugging: () -> Unit,
    onNavigateToTerminalIO: () -> Unit,
    onNavigateToTerminalView: () -> Unit
) {
    Scaffold(
        topBar = {
            SettingsTopBar(
                title = stringResource(id = R.string.title_activity_termux_settings),
                showBack = true,
                onBack = onBack,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Spacer(modifier = Modifier.height(12.dp))
            SettingsCard {
                SettingsClickableRow(
                    title = stringResource(id = R.string.termux_debugging_preferences_title),
                    subtitle = stringResource(id = R.string.termux_debugging_preferences_summary),
                    onClick = onNavigateToDebugging,
                    showDivider = true
                )
                SettingsClickableRow(
                    title = stringResource(id = R.string.termux_terminal_io_preferences_title),
                    subtitle = stringResource(id = R.string.termux_terminal_io_preferences_summary),
                    onClick = onNavigateToTerminalIO,
                    showDivider = true
                )
                SettingsClickableRow(
                    title = stringResource(id = R.string.termux_terminal_view_preferences_title),
                    subtitle = stringResource(id = R.string.termux_terminal_view_preferences_summary),
                    onClick = onNavigateToTerminalView,
                    showDivider = false
                )
            }
        }
    }
}

@Composable
private fun DebuggingSettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { TermuxAppSharedPreferences.build(context, true) }

    var logLevel by remember { mutableStateOf(preferences?.getLogLevel() ?: Logger.DEFAULT_LOG_LEVEL) }
    var terminalViewKeyLogging by remember { mutableStateOf(preferences?.isTerminalViewKeyLoggingEnabled() ?: false) }
    var pluginErrorNotifications by remember { mutableStateOf(preferences?.arePluginErrorNotificationsEnabled(false) ?: true) }
    var crashReportNotifications by remember { mutableStateOf(preferences?.areCrashReportNotificationsEnabled(false) ?: true) }
    var showLogLevelDialog by remember { mutableStateOf(false) }

    val logLevels = Logger.getLogLevelsArray()
    val logLevelLabels = Logger.getLogLevelLabelsArray(context, logLevels, true)
    var currentLogLevelLabel = ""
    for (index in logLevels.indices) {
        if (logLevels[index].toString().toInt() == logLevel) {
            currentLogLevelLabel = logLevelLabels[index].toString()
        }
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = stringResource(id = R.string.termux_debugging_preferences_title),
                showBack = true,
                onBack = onBack,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SettingsGroupHeader(text = stringResource(id = R.string.termux_logging_header))
            SettingsCard {
                SettingsClickableRow(
                    title = stringResource(id = R.string.termux_log_level_title),
                    subtitle = currentLogLevelLabel,
                    onClick = { showLogLevelDialog = true },
                    showDivider = true
                )
                SettingsSwitchRow(
                    title = stringResource(id = R.string.termux_terminal_view_key_logging_enabled_title),
                    subtitle = if (terminalViewKeyLogging) {
                        stringResource(id = R.string.termux_terminal_view_key_logging_enabled_on)
                    } else {
                        stringResource(id = R.string.termux_terminal_view_key_logging_enabled_off)
                    },
                    checked = terminalViewKeyLogging,
                    onCheckedChange = { value ->
                        terminalViewKeyLogging = value
                        preferences?.setTerminalViewKeyLoggingEnabled(value)
                    },
                    showDivider = true
                )
                SettingsSwitchRow(
                    title = stringResource(id = R.string.termux_plugin_error_notifications_enabled_title),
                    subtitle = if (pluginErrorNotifications) {
                        stringResource(id = R.string.termux_plugin_error_notifications_enabled_on)
                    } else {
                        stringResource(id = R.string.termux_plugin_error_notifications_enabled_off)
                    },
                    checked = pluginErrorNotifications,
                    onCheckedChange = { value ->
                        pluginErrorNotifications = value
                        preferences?.setPluginErrorNotificationsEnabled(value)
                    },
                    showDivider = true
                )
                SettingsSwitchRow(
                    title = stringResource(id = R.string.termux_crash_report_notifications_enabled_title),
                    subtitle = if (crashReportNotifications) {
                        stringResource(id = R.string.termux_crash_report_notifications_enabled_on)
                    } else {
                        stringResource(id = R.string.termux_crash_report_notifications_enabled_off)
                    },
                    checked = crashReportNotifications,
                    onCheckedChange = { value ->
                        crashReportNotifications = value
                        preferences?.setCrashReportNotificationsEnabled(value)
                    },
                    showDivider = false
                )
            }
        }
    }

    if (showLogLevelDialog) {
        LogLevelDialog(
            currentValue = logLevel,
            onDismiss = { showLogLevelDialog = false },
            onSelect = { value ->
                logLevel = value
                preferences?.setLogLevel(context, value)
            }
        )
    }
}

@Composable
private fun TerminalIOSettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { TermuxAppSharedPreferences.build(context, true) }

    var softKeyboardEnabled by remember { mutableStateOf(preferences?.isSoftKeyboardEnabled() ?: true) }
    var softKeyboardOnlyIfNoHardware by remember { mutableStateOf(preferences?.isSoftKeyboardEnabledOnlyIfNoHardware() ?: false) }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = stringResource(id = R.string.termux_terminal_io_preferences_title),
                showBack = true,
                onBack = onBack,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SettingsGroupHeader(text = stringResource(id = R.string.termux_keyboard_header))
            SettingsCard {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.termux_soft_keyboard_enabled_title),
                    subtitle = if (softKeyboardEnabled) {
                        stringResource(id = R.string.termux_soft_keyboard_enabled_on)
                    } else {
                        stringResource(id = R.string.termux_soft_keyboard_enabled_off)
                    },
                    checked = softKeyboardEnabled,
                    onCheckedChange = { value ->
                        softKeyboardEnabled = value
                        preferences?.setSoftKeyboardEnabled(value)
                    },
                    showDivider = true
                )
                SettingsSwitchRow(
                    title = stringResource(id = R.string.termux_soft_keyboard_enabled_only_if_no_hardware_title),
                    subtitle = if (softKeyboardOnlyIfNoHardware) {
                        stringResource(id = R.string.termux_soft_keyboard_enabled_only_if_no_hardware_on)
                    } else {
                        stringResource(id = R.string.termux_soft_keyboard_enabled_only_if_no_hardware_off)
                    },
                    checked = softKeyboardOnlyIfNoHardware,
                    onCheckedChange = { value ->
                        softKeyboardOnlyIfNoHardware = value
                        preferences?.setSoftKeyboardEnabledOnlyIfNoHardware(value)
                    },
                    showDivider = false
                )
            }
        }
    }
}

@Composable
private fun TerminalViewSettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { TermuxAppSharedPreferences.build(context, true) }

    var terminalMarginAdjustment by remember { mutableStateOf(preferences?.isTerminalMarginAdjustmentEnabled() ?: true) }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = stringResource(id = R.string.termux_terminal_view_preferences_title),
                showBack = true,
                onBack = onBack,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SettingsGroupHeader(text = stringResource(id = R.string.termux_terminal_view_view_header))
            SettingsCard {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.termux_terminal_view_terminal_margin_adjustment_title),
                    subtitle = if (terminalMarginAdjustment) {
                        stringResource(id = R.string.termux_terminal_view_terminal_margin_adjustment_on)
                    } else {
                        stringResource(id = R.string.termux_terminal_view_terminal_margin_adjustment_off)
                    },
                    checked = terminalMarginAdjustment,
                    onCheckedChange = { value ->
                        terminalMarginAdjustment = value
                        preferences?.setTerminalMarginAdjustment(value)
                    },
                    showDivider = false
                )
            }
        }
    }
}

/** A single row entry for the Material 3 terminal long-press context menu. */
data class TerminalMenuItem(
    val id: Int,
    val title: String,
    val enabled: Boolean,
    val checkable: Boolean,
    val checked: Boolean
)

/** Java-friendly SAM listener invoked when a [TerminalMenuItem] is tapped. */
fun interface TerminalMenuItemClickListener {
    fun onItemClick(itemId: Int)
}

/** Java-friendly SAM listener invoked when the terminal context menu is dismissed. */
fun interface TerminalMenuDismissListener {
    fun onDismiss()
}

/**
 * Holds the state for the Material 3 terminal long-press context menu so it can be driven
 * from Java (see [TermuxActivity]) without needing Composable syntax on the Java side.
 */
class TerminalContextMenuState {
    var visible: Boolean by mutableStateOf(false)
        private set

    var items: List<TerminalMenuItem> by mutableStateOf(emptyList())
        private set

    private var itemClickListener: TerminalMenuItemClickListener? = null
    private var dismissListener: TerminalMenuDismissListener? = null

    fun show(
        items: List<TerminalMenuItem>,
        itemClickListener: TerminalMenuItemClickListener,
        dismissListener: TerminalMenuDismissListener
    ) {
        this.items = items
        this.itemClickListener = itemClickListener
        this.dismissListener = dismissListener
        this.visible = true
    }

    fun dismiss() {
        if (visible) {
            visible = false
            dismissListener?.onDismiss()
        }
    }

    fun handleItemClick(itemId: Int) {
        itemClickListener?.onItemClick(itemId)
        dismiss()
    }
}

/**
 * Attaches a full-screen [ComposeView] hosting [TerminalContextMenuHost] on top of [container]
 * (typically `android.R.id.content`). Ordinary (non-Composable) function so it is callable
 * directly from Java.
 */
fun attachTerminalContextMenuComposeView(
    activity: AppCompatActivity,
    container: ViewGroup,
    state: TerminalContextMenuState
): ComposeView {
    val composeView = ComposeView(activity)
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    composeView.setContent {
        TerminalContextMenuHost(state = state)
    }
    container.addView(
        composeView,
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    )
    return composeView
}

/** Maps a `CONTEXT_MENU_*_ID` constant from [TermuxActivity] to a representative Material icon. */
private fun iconForTerminalMenuItem(itemId: Int): ImageVector? {
    return when (itemId) {
        0 -> Icons.Filled.Link              // CONTEXT_MENU_SELECT_URL_ID
        1 -> Icons.Filled.Share             // CONTEXT_MENU_SHARE_TRANSCRIPT_ID
        10 -> Icons.Filled.ContentCopy      // CONTEXT_MENU_SHARE_SELECTED_TEXT
        11 -> Icons.Filled.Person           // CONTEXT_MENU_AUTOFILL_USERNAME
        2 -> Icons.Filled.Lock              // CONTEXT_MENU_AUTOFILL_PASSWORD
        3 -> Icons.Filled.RestartAlt        // CONTEXT_MENU_RESET_TERMINAL_ID
        4 -> Icons.Filled.Cancel            // CONTEXT_MENU_KILL_PROCESS_ID
        5 -> Icons.Filled.Palette           // CONTEXT_MENU_STYLING_ID
        7 -> Icons.AutoMirrored.Filled.Help // CONTEXT_MENU_HELP_ID
        8 -> Icons.Filled.Settings          // CONTEXT_MENU_SETTINGS_ID
        9 -> Icons.Filled.BugReport         // CONTEXT_MENU_REPORT_ID
        else -> null
    }
}

@Composable
fun TerminalContextMenuHost(state: TerminalContextMenuState) {
    TermuxSettingsTheme {
        if (state.visible) {
            Dialog(onDismissRequest = { state.dismiss() }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth(0.88f)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        state.items.forEachIndexed { index, item ->
                            TerminalMenuRow(
                                item = item,
                                onClick = { state.handleItemClick(item.id) }
                            )
                            if (index != state.items.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalMenuRow(item: TerminalMenuItem, onClick: () -> Unit) {
    val contentColor = if (item.enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = iconForTerminalMenuItem(item.id)
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(20.dp))
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (item.checkable) {
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = item.checked,
                onCheckedChange = { onClick() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

class TerminalDialogState {
    var renameSessionVisible by mutableStateOf(false)
        private set
    var sessionToRename: TerminalSession? by mutableStateOf(null)
        private set

    var killSessionVisible by mutableStateOf(false)
        private set
    var sessionToKill: TerminalSession? by mutableStateOf(null)
        private set

    fun requestRenameSession(session: TerminalSession) {
        sessionToRename = session
        renameSessionVisible = true
    }

    fun dismissRenameSession() {
        renameSessionVisible = false
        sessionToRename = null
    }

    var reportIssueVisible by mutableStateOf(false)
        private set
    var transcriptForReport: String? by mutableStateOf(null)
        private set

    var stylingInstallVisible by mutableStateOf(false)
        private set

    fun requestKillSession(session: TerminalSession) {
        sessionToKill = session
        killSessionVisible = true
    }

    fun dismissKillSession() {
        killSessionVisible = false
        sessionToKill = null
    }

    fun requestReportIssue(transcript: String) {
        transcriptForReport = transcript
        reportIssueVisible = true
    }

    fun dismissReportIssue() {
        reportIssueVisible = false
        transcriptForReport = null
    }

    fun requestStylingInstall() {
        stylingInstallVisible = true
    }

    fun dismissStylingInstall() {
        stylingInstallVisible = false
    }
}

fun attachTerminalDialogComposeView(
    activity: AppCompatActivity,
    container: ViewGroup,
    state: TerminalDialogState,
    termuxActivity: TermuxActivity
): ComposeView {
    val composeView = ComposeView(activity)
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    composeView.setContent {
        TerminalDialogHost(state = state, activity = termuxActivity)
    }
    container.addView(
        composeView,
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    )
    return composeView
}

@Composable
fun TerminalDialogHost(state: TerminalDialogState, activity: TermuxActivity) {
    TermuxSettingsTheme {
        if (state.renameSessionVisible) {
            val session = state.sessionToRename
            var text by remember { mutableStateOf(session?.mSessionName ?: "") }
            AlertDialog(
                onDismissRequest = { state.dismissRenameSession() },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.title_rename_session),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (session != null) {
                                session.mSessionName = text
                                val service = activity.termuxService
                                if (service != null) {
                                    val termuxSession = service.getTermuxSessionForTerminalSession(session)
                                    if (termuxSession != null) {
                                        termuxSession.executionCommand.shellName = text
                                    }
                                }
                                activity.termuxSessionListNotifyUpdated()
                            }
                            state.dismissRenameSession()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.action_rename_session_confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { state.dismissRenameSession() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 6.dp
            )
        }

        if (state.killSessionVisible) {
            val session = state.sessionToKill
            AlertDialog(
                onDismissRequest = { state.dismissKillSession() },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Cancel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.title_confirm_kill_process),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            session?.finishIfRunning()
                            state.dismissKillSession()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { state.dismissKillSession() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 6.dp
            )
        }

        if (state.reportIssueVisible) {
            val transcript = state.transcriptForReport
            AlertDialog(
                onDismissRequest = { state.dismissReportIssue() },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = "${TermuxConstants.TERMUX_APP_NAME} Report Issue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.msg_add_termux_debug_info),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (transcript != null) {
                                activity.termuxTerminalViewClient.reportIssueFromTranscript(transcript, true)
                            }
                            state.dismissReportIssue()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(stringResource(com.termux.shared.R.string.action_yes))
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            if (transcript != null) {
                                activity.termuxTerminalViewClient.reportIssueFromTranscript(transcript, false)
                            }
                            state.dismissReportIssue()
                        }
                    ) {
                        Text(stringResource(com.termux.shared.R.string.action_no))
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 6.dp
            )
        }

        if (state.stylingInstallVisible) {
            AlertDialog(
                onDismissRequest = { state.dismissStylingInstall() },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = "Termux:Styling",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.error_styling_not_installed),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            com.termux.shared.activity.ActivityUtils.startActivity(activity, Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL)))
                            state.dismissStylingInstall()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.action_styling_install))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { state.dismissStylingInstall() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 6.dp
            )
        }
    }
}