package com.r3zound.music.feature.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.r3zound.music.core.model.ChannelHealth
import com.r3zound.music.core.model.ChannelInfo
import com.r3zound.music.data.remote.ProviderRegistry
import com.r3zound.music.data.repo.MusicRepository
import com.r3zound.music.ui.theme.MuPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** 渠道体检 UI 状态。 */
data class ChannelHealthUiState(
    /** 各渠道体检结果。 */
    val channels: List<ChannelInfo> = emptyList(),
    /** 是否正在检测。 */
    val checking: Boolean = false,
    /** 最近一次检测完成时间。 */
    val checkedAt: Long = 0L,
    /** 检测失败原因。 */
    val error: String? = null,
)

/**
 * 渠道体检 ViewModel。
 *
 * 检测逻辑复用仓库的 `checkChannels`：并发探测每个渠道的「搜索 + 取流」，
 * 结果会缓存在仓库里，设置页的「N 可用」徽标读的就是这份缓存。
 */
@HiltViewModel
class ChannelHealthViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelHealthUiState())
    val state: StateFlow<ChannelHealthUiState> = _state.asStateFlow()

    init {
        // 先展示上次体检结果，避免页面空白
        viewModelScope.launch {
            val cached = runCatching { musicRepository.cachedHealth() }.getOrDefault(emptyList())
            if (cached.isNotEmpty()) _state.update { it.copy(channels = cached) }
        }
    }

    /** 跑一次全渠道体检。 */
    fun check() {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            runCatching { musicRepository.checkChannels() }
                .onSuccess { list ->
                    _state.update {
                        it.copy(
                            channels = list,
                            checking = false,
                            checkedAt = System.currentTimeMillis(),
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(checking = false, error = e.message ?: "检测失败") }
                }
        }
    }
}

/** 渠道体检页。 */
@Composable
fun ChannelHealthScreen(navController: NavHostController) {
    val viewModel: ChannelHealthViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 进入页面自动跑一次
    LaunchedEffect(Unit) { viewModel.check() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HealthTopBar(
            onBack = { navController.popBackStack() },
            onRefresh = { viewModel.check() },
            refreshing = state.checking,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "overview") { HealthOverviewCard(state) }
            item(key = "listHeader") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "全部渠道",
                        style = MaterialTheme.typography.labelSmall,
                        color = MuPalette.ink3,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${state.channels.size}/${ProviderRegistry.all.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MuPalette.ink3,
                    )
                }
            }
            val channels = state.channels
            if (channels.isEmpty()) {
                // 还没有任何结果：显示骨架占位
                items(ProviderRegistry.all.size) { index ->
                    ChannelSkeletonRow(ProviderRegistry.all[index].name)
                }
            } else {
                items(channels, key = { it.id }) { info ->
                    ChannelHealthRow(info = info, loading = state.checking)
                }
            }
        }
    }
}

/** 顶栏：返回 + 标题 + 重新检测。 */
@Composable
private fun HealthTopBar(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(2.dp))
        Text(
            text = "渠道体检",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = !refreshing, onClick = onRefresh)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
            } else {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = "重新检测",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 总览卡片：OK / PARTIAL / DOWN 各多少个。 */
@Composable
private fun HealthOverviewCard(state: ChannelHealthUiState) {
    val ok = state.channels.count { it.health == ChannelHealth.OK }
    val partial = state.channels.count { it.health == ChannelHealth.PARTIAL }
    val down = state.channels.count { it.health == ChannelHealth.DOWN }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "渠道总览",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    state.checking -> "检测中…"
                    state.checkedAt > 0L -> "最近检测 ${formatTime(state.checkedAt)}"
                    else -> "尚未检测"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MuPalette.ink3,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("可用", ok, MuPalette.success, Modifier.weight(1f))
            StatBox("取流失败", partial, MuPalette.qualityAmber, Modifier.weight(1f))
            StatBox("不可用", down, MuPalette.ink3, Modifier.weight(1f))
        }
        state.error?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 统计小块。 */
@Composable
private fun StatBox(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
            color = color,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MuPalette.ink3,
        )
    }
}

/** 单个渠道行。 */
@Composable
private fun ChannelHealthRow(info: ChannelInfo, loading: Boolean) {
    val dotColor = healthColor(info.health)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = info.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = info.detail ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MuPalette.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "检测中",
                    fontSize = 11.sp,
                    color = MuPalette.ink3,
                )
            }
        } else {
            Text(
                text = info.health.label,
                style = MaterialTheme.typography.bodySmall,
                color = dotColor,
            )
        }
    }
}

/** 骨架行：还没有检测结果时占位。 */
@Composable
private fun ChannelSkeletonRow(name: String) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline)
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha))
            )
        }
        Spacer(Modifier.width(8.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.5.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 健康状态对应的颜色：OK 绿 / PARTIAL 琥珀 / DOWN 灰。 */
@Composable
private fun healthColor(health: ChannelHealth): Color = when (health) {
    ChannelHealth.OK -> MuPalette.success
    ChannelHealth.PARTIAL -> MuPalette.qualityAmber
    ChannelHealth.DOWN -> MuPalette.ink3
    ChannelHealth.UNKNOWN -> MuPalette.ink3
}

/** 时间戳 → MM-dd HH:mm。 */
private fun formatTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
