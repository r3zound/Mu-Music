package com.r3zound.music.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.r3zound.music.BuildConfig
import com.r3zound.music.data.prefs.SettingsStore
import com.r3zound.music.data.prefs.ThemeMode
import com.r3zound.music.ui.components.SecondaryButton
import com.r3zound.music.ui.components.SectionHeader
import com.r3zound.music.ui.nav.Routes
import com.r3zound.music.ui.theme.MuPalette
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 版本号直接读 BuildConfig，避免跟 APK 里的 versionName 对不上。 */
private val APP_VERSION = BuildConfig.VERSION_NAME
/** 唯一的对外链接：项目开源地址。 */
private const val GITHUB_REPO = "https://github.com/r3zound/Mu-Music"

/** 设置页。 */
@Composable
fun SettingsScreen(navController: NavHostController) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showBaseUrl by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showConcurrent by remember { mutableStateOf(false) }
    var showRetry by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showClearCache by remember { mutableStateOf(false) }
    var cacheSizeText by remember { mutableStateOf("—") }

    // 导入备份文件的选择器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { viewModel.importFavoritesFromUri(uri) }
                .onSuccess { result ->
                    snackbarHostState.showSnackbar(
                        "已导入 ${result.songs} 首 · ${result.playlists} 个歌单 · ${result.tags} 个标签"
                    )
                }
                .onFailure { e ->
                    snackbarHostState.showSnackbar("导入失败：${e.message ?: "文件格式不正确"}")
                }
        }
    }

    // 进入页面时同步渠道体检结果与缓存占用
    LaunchedEffect(Unit) {
        viewModel.refreshChannelHealth()
        cacheSizeText = formatSize(calcCacheSize(context))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Mu 音乐 · 音源与播放偏好",
                style = MaterialTheme.typography.bodySmall,
                color = MuPalette.ink3,
            )

            // ── 音源 ────────────────────────────────────────────────
            SectionHeader("音源")
            SettingsGroup {
                SettingRow(
                    icon = Icons.Outlined.Dns,
                    title = "服务器地址",
                    subtitle = "音源站地址，保存后立即生效",
                    onClick = { showBaseUrl = true },
                    trailing = {
                        ValueText(
                            text = state.baseUrl.ifBlank { "未设置" },
                            mono = true,
                            highlight = state.baseUrl.isBlank(),
                        )
                        Spacer(Modifier.width(2.dp))
                        ArrowIcon()
                    },
                )
                GroupDivider()
                SettingRow(
                    icon = Icons.Filled.CheckCircle,
                    title = "渠道体检",
                    subtitle = if (state.lastCheckedAt > 0L) {
                        "最近检测 ${formatTime(state.lastCheckedAt)}"
                    } else {
                        "检测各渠道的搜索与取流是否可用"
                    },
                    onClick = { navController.navigate(Routes.CHANNEL_HEALTH) },
                    trailing = {
                        Badge(
                            text = "${state.availableChannels} 可用",
                            color = if (state.availableChannels > 0) MuPalette.success else MuPalette.ink3,
                        )
                        Spacer(Modifier.width(2.dp))
                        ArrowIcon()
                    },
                )
            }

            // ── 下载 ────────────────────────────────────────────────
            SectionHeader("下载")
            SettingsGroup {
                SettingRow(
                    icon = Icons.Outlined.Folder,
                    title = "保存位置",
                    subtitle = "Android 10+ 自动进媒体库",
                    clickable = false,
                    trailing = { ValueText("Music/MuMusic", mono = true) },
                )
                GroupDivider()
                SettingRow(
                    icon = Icons.Outlined.Wifi,
                    title = "仅 WiFi 下载",
                    subtitle = "移动网络下自动暂停下载任务",
                    trailing = {
                        Switch(
                            checked = state.wifiOnly,
                            onCheckedChange = viewModel::setWifiOnly,
                        )
                    },
                )
                GroupDivider()
                SettingRow(
                    icon = Icons.Outlined.HighQuality,
                    title = "默认音质",
                    subtitle = "下载时优先选择的档位",
                    onClick = { showQuality = true },
                    trailing = {
                        ValueText(qualityLabel(state.defaultQuality))
                        Spacer(Modifier.width(2.dp))
                        ArrowIcon()
                    },
                )
                GroupDivider()
                SettingRow(
                    icon = Icons.Outlined.Layers,
                    title = "最大并发数",
                    subtitle = "同时进行的下载任务上限",
                    onClick = { showConcurrent = true },
                    trailing = {
                        ValueText("${state.maxConcurrent} 个")
                        Spacer(Modifier.width(2.dp))
                        ArrowIcon()
                    },
                )
                GroupDivider()
                SettingRow(
                    icon = Icons.Outlined.Refresh,
                    title = "失败自动重试",
                    subtitle = "下载失败后的自动重试次数",
                    onClick = { showRetry = true },
                    trailing = {
                        ValueText("${state.autoRetry} 次")
                        Spacer(Modifier.width(2.dp))
                        ArrowIcon()
                    },
                )
            }

            // ── 播放 ────────────────────────────────────────────────
            SectionHeader("播放")
            SettingsGroup {
                SettingRow(
                    icon = Icons.Outlined.Palette,
                    title = "主题",
                    subtitle = "深色优先，支持跟随系统",
                    onClick = { showTheme = true },
                    trailing = {
                        ValueText(themeLabel(state.themeMode))
                        Spacer(Modifier.width(2.dp))
                        ArrowIcon()
                    },
                )
                GroupDivider()
                SettingRow(
                    icon = Icons.Outlined.Notifications,
                    title = "下载完成通知",
                    subtitle = "下载结束后发送系统通知",
                    trailing = {
                        Switch(
                            checked = state.notificationEnabled,
                            onCheckedChange = viewModel::setNotificationEnabled,
                        )
                    },
                )
            }

            // ── 数据 ────────────────────────────────────────────────
            SectionHeader("数据")
            SettingsGroup {
                SettingRow(
                    icon = Icons.Outlined.DeleteSweep,
                    title = "清空缓存",
                    subtitle = "清理封面等临时文件，已下载歌曲不受影响",
                    onClick = { showClearCache = true },
                    trailing = {
                        ValueText(cacheSizeText)
                        Spacer(Modifier.width(2.dp))
                        ArrowIcon()
                    },
                )
                GroupDivider()
                SettingRow(
                    icon = Icons.Outlined.FileDownload,
                    title = "导出收藏夹",
                    subtitle = "把歌单、标签与收藏导出为 JSON 备份",
                    onClick = {
                        scope.launch {
                            runCatching { viewModel.exportFavorites() }
                                .onSuccess { path ->
                                    snackbarHostState.showSnackbar("已导出到 $path")
                                }
                                .onFailure { e ->
                                    snackbarHostState.showSnackbar("导出失败：${e.message ?: "未知错误"}")
                                }
                        }
                    },
                    trailing = {
                        ValueText("JSON")
                        Spacer(Modifier.width(2.dp))
                        ArrowIcon()
                    },
                )
                GroupDivider()
                SettingRow(
                    icon = Icons.Outlined.FileUpload,
                    title = "导入收藏夹",
                    subtitle = "从 JSON 备份恢复，同名歌单与同一首歌会自动合并",
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    trailing = {
                        ValueText("JSON")
                        Spacer(Modifier.width(2.dp))
                        ArrowIcon()
                    },
                )
            }

            // ── 关于 ────────────────────────────────────────────────
            SectionHeader("关于")
            SettingsGroup {
                SettingRow(
                    icon = Icons.Outlined.Link,
                    title = "版本号",
                    subtitle = "MuMusic",
                    clickable = false,
                    trailing = { ValueText(APP_VERSION, mono = true) },
                )
                GroupDivider()
                SettingRow(
                    icon = Icons.Outlined.Language,
                    title = "开源地址",
                    subtitle = GITHUB_REPO,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO))
                        runCatching { context.startActivity(intent) }
                    },
                    trailing = {
                        ValueText(GITHUB_REPO, mono = true)
                        Spacer(Modifier.width(2.dp))
                        ArrowIcon()
                    },
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    // ── 对话框 ────────────────────────────────────────────────────
    if (showBaseUrl) {
        BaseUrlDialog(
            current = state.baseUrl,
            onDismiss = { showBaseUrl = false },
            onConfirm = {
                viewModel.setBaseUrl(it)
                showBaseUrl = false
            },
            onTest = { viewModel.testConnection(it) },
        )
    }

    if (showQuality) {
        SingleChoiceDialog(
            title = "默认音质",
            options = NETEASE_QUALITY_TIERS.map { ChoiceOption(it.value, it.label, it.subtitle) },
            selected = state.defaultQuality,
            onSelect = {
                viewModel.setDefaultQuality(it)
                showQuality = false
            },
            onDismiss = { showQuality = false },
        )
    }

    if (showConcurrent) {
        SingleChoiceDialog(
            title = "最大并发数",
            options = (1..5).map { ChoiceOption(it.toString(), "$it 个") },
            selected = state.maxConcurrent.toString(),
            onSelect = {
                viewModel.setMaxConcurrent(it.toIntOrNull() ?: 2)
                showConcurrent = false
            },
            onDismiss = { showConcurrent = false },
        )
    }

    if (showRetry) {
        SingleChoiceDialog(
            title = "失败自动重试",
            options = (0..5).map { ChoiceOption(it.toString(), "$it 次") },
            selected = state.autoRetry.toString(),
            onSelect = {
                viewModel.setAutoRetry(it.toIntOrNull() ?: 3)
                showRetry = false
            },
            onDismiss = { showRetry = false },
        )
    }

    if (showTheme) {
        SingleChoiceDialog(
            title = "主题",
            options = THEME_OPTIONS.map { ChoiceOption(it.mode.name, it.label) },
            selected = state.themeMode.name,
            onSelect = {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()?.let(viewModel::setThemeMode)
                showTheme = false
            },
            onDismiss = { showTheme = false },
        )
    }

    if (showClearCache) {
        AlertDialog(
            onDismissRequest = { showClearCache = false },
            title = { Text("清空缓存", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    text = "将清理封面等临时缓存文件，以及未完成的下载临时文件。已下载的歌曲与收藏不受影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MuPalette.ink2,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val freed = clearCache(context)
                        cacheSizeText = formatSize(calcCacheSize(context))
                        showClearCache = false
                        scope.launch { snackbarHostState.showSnackbar("已清理 ${formatSize(freed)}") }
                    },
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCache = false }) { Text("取消") }
            },
        )
    }
}

// ── 组件 ──────────────────────────────────────────────────────────

/** 一组设置项。 */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        content = content,
    )
}

/** 组内分隔线。 */
@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 53.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/** 设置行：29dp 圆角图标块 + 标题 + 副标题 + 右侧内容。 */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    clickable: Boolean = true,
    onClick: () -> Unit = {},
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(29.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MuPalette.ink3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** 右侧取值文本。 */
@Composable
private fun ValueText(text: String, mono: Boolean = false, highlight: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        // 「未设置」用品牌红提示需要用户处理
        color = if (highlight) MaterialTheme.colorScheme.primary else MuPalette.ink3,
        fontFamily = if (mono) FontFamily.Monospace else null,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.End,
        modifier = Modifier.widthIn(max = 150.dp),
    )
}

/** 右侧箭头。 */
@Composable
private fun ArrowIcon() {
    Icon(
        imageVector = Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MuPalette.ink3,
        modifier = Modifier.size(18.dp),
    )
}

/** 小徽标。 */
@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

// ── 对话框 ────────────────────────────────────────────────────────

/** 服务器地址编辑 + 连接测试。 */
@Composable
private fun BaseUrlDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onTest: suspend (String) -> ConnectionTestResult,
) {
    var input by remember { mutableStateOf(current) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ConnectionTestResult?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("服务器地址", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    text = "填写音源站地址，保存后立即生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MuPalette.ink3,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        result = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("地址") },
                    placeholder = { Text("https://your-music-server.com", fontSize = 12.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SecondaryButton(
                        text = if (testing) "检测中…" else "测试连接",
                        onClick = {
                            if (!testing) {
                                scope.launch {
                                    testing = true
                                    result = runCatching { onTest(input) }
                                        .getOrElse { ConnectionTestResult(false, it.message ?: "测试失败") }
                                    testing = false
                                }
                            }
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    result?.let { r ->
                        Text(
                            text = if (r.success) "连接正常" else r.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (r.success) MuPalette.success else MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 单选对话框。 */
@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<ChoiceOption>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(option.value) }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option.value == selected,
                            onClick = { onSelect(option.value) },
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            option.subtitle?.let {
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MuPalette.ink3,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

// ── 辅助 ──────────────────────────────────────────────────────────

/** 音质 value → 展示名。 */
private fun qualityLabel(value: String): String =
    NETEASE_QUALITY_TIERS.firstOrNull { it.value == value }?.label ?: value

/** 主题 → 展示名。 */
private fun themeLabel(mode: ThemeMode): String =
    THEME_OPTIONS.firstOrNull { it.mode == mode }?.label ?: mode.name

/** 时间戳 → MM-dd HH:mm。 */
private fun formatTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

/** 目录占用。 */
private fun dirSize(file: File): Long {
    if (!file.exists()) return 0L
    if (file.isFile) return file.length()
    return file.listFiles()?.sumOf { dirSize(it) } ?: 0L
}

/** 缓存占用：应用缓存目录 + 下载临时目录。 */
private fun calcCacheSize(context: Context): Long {
    var total = 0L
    context.cacheDir.listFiles()?.forEach { total += dirSize(it) }
    File(context.filesDir, "downloads").listFiles()?.forEach { total += dirSize(it) }
    return total
}

/**
 * 清空缓存。
 *
 * 只删 `cacheDir` 的内容和 `filesDir/downloads` 子目录，**不删 filesDir 本身**，
 * 否则收藏、数据库等应用数据会一起丢失。
 */
private fun clearCache(context: Context): Long {
    val freed = calcCacheSize(context)
    runCatching { context.cacheDir.deleteRecursively() }
    runCatching { context.cacheDir.mkdirs() } // 删完立刻重建，避免后续写入失败
    runCatching { File(context.filesDir, "downloads").deleteRecursively() }
    return freed
}

/** 人类可读的体积。 */
private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
}
