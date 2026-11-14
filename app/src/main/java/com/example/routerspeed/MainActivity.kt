package com.example.routerspeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF006782),
                    primaryContainer = Color(0xFFBCE9FF),
                    secondaryContainer = Color(0xFFE6EEF2),
                )
            ) {
                val model: RouterViewModel = viewModel()
                RouterApp(model)
            }
        }
    }
}

@Composable
private fun RouterApp(model: RouterViewModel) {
    var address by rememberSaveable { mutableStateOf(model.initialAddress) }
    var password by rememberSaveable { mutableStateOf(model.initialPassword) }
    var rememberPassword by rememberSaveable { mutableStateOf(model.initialRememberPassword) }
    val state = model.uiState

    if (state.loggedIn) {
        DeviceScreen(
            state = state,
            onRefresh = { model.refresh() },
            onLogout = model::logout,
            onDismissError = model::dismissError,
        )
    } else {
        LoginScreen(
            address = address,
            password = password,
            rememberPassword = rememberPassword,
            connecting = state.connecting,
            error = state.error,
            onAddressChange = { address = it },
            onPasswordChange = { password = it },
            onRememberPasswordChange = { rememberPassword = it },
            onLogin = { model.login(address, password, rememberPassword) },
        )
    }
}

@Composable
private fun LoginScreen(
    address: String,
    password: String,
    rememberPassword: Boolean,
    connecting: Boolean,
    error: String?,
    onAddressChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRememberPasswordChange: (Boolean) -> Unit,
    onLogin: () -> Unit,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "3.1Routerspeed",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "仅支持 3.1 路由器 · 按实时网速累计活跃度",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = onAddressChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("路由器地址") },
                        supportingText = { Text("例如：192.168.3.1") },
                        singleLine = true,
                        enabled = !connecting,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("管理员密码") },
                        supportingText = { Text("用户名固定为 admin") },
                        singleLine = true,
                        enabled = !connecting,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                enabled = !connecting,
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (passwordVisible) R.drawable.ic_visibility_off
                                        else R.drawable.ic_visibility
                                    ),
                                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onLogin() }),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberPassword,
                            onCheckedChange = onRememberPasswordChange,
                            enabled = !connecting,
                        )
                        Text(
                            text = "记住密码",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "下次启动自动登录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (error != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !connecting,
                    ) {
                        if (connecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("正在登录")
                        } else {
                            Text("登录并查看设备")
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "请确保手机已连接该路由器的 Wi-Fi。\n密码仅用于本机登录，不会上传。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DeviceScreen(
    state: RouterUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onDismissError: () -> Unit,
) {
    val expandedState = rememberSaveable { mutableStateOf<String?>(null) }
    val expandedKey = expandedState.value
    val onToggle = remember {
        { key: String ->
            expandedState.value = if (expandedState.value == key) null else key
        }
    }
    val totalUp = state.devices.sumOf { it.device.upRate.coerceAtLeast(0) }
    val totalDown = state.devices.sumOf { it.device.downRate.coerceAtLeast(0) }

    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggle(ROUTER_HISTORY_KEY) }
                        .padding(vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "路由器总速度 · ${state.devices.size} 台",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (expandedKey == ROUTER_HISTORY_KEY) "收起" else "曲线",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "↑",
                            color = UploadSpeedColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = formatRate(totalUp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "↓",
                            color = DownloadSpeedColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = formatRate(totalDown),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
                TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                    Text(if (state.refreshing) "刷新中" else "刷新")
                }
                TextButton(onClick = onLogout) { Text("退出") }
            }
            if (state.refreshing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (expandedKey == ROUTER_HISTORY_KEY) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    SpeedChart(
                        points = state.histories[ROUTER_HISTORY_KEY].orEmpty(),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (state.error != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.error,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onDismissError) { Text("关闭") }
                    }
                }
            }
            if (state.devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "路由器暂未返回设备",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        items = state.devices,
                        key = { _, item -> item.device.trackingKey },
                        contentType = { _, _ -> "device" },
                    ) { index, activeDevice ->
                        val key = activeDevice.device.trackingKey
                        val expanded = expandedKey == key
                        DeviceCard(
                            rank = index + 1,
                            activeDevice = activeDevice,
                            expanded = expanded,
                            history = if (expanded) state.histories[key].orEmpty() else emptyList(),
                            onToggle = onToggle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    rank: Int,
    activeDevice: ActiveDevice,
    expanded: Boolean,
    history: List<SpeedPoint>,
    onToggle: (String) -> Unit,
) {
    val device = activeDevice.device
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(device.trackingKey) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            DeviceSummary(rank = rank, device = device, expanded = expanded)
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                DeviceDetails(activeDevice = activeDevice, history = history)
            }
        }
    }
}

@Composable
private fun DeviceSummary(rank: Int, device: Device, expanded: Boolean) {
    val upText = remember(device.upRate) { formatRate(device.upRate) }
    val downText = remember(device.downRate) { formatRate(device.downRate) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "#$rank",
            modifier = Modifier.width(30.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = device.displayName.ifBlank { "未知设备" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (expanded) "收起" else "详情",
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "↑",
            color = UploadSpeedColor,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = upText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = "↓",
            color = DownloadSpeedColor,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = downText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun DeviceDetails(activeDevice: ActiveDevice, history: List<SpeedPoint>) {
    val device = activeDevice.device
    val connection = when {
        device.interfaceType.contains("wireless", ignoreCase = true) ->
            listOf("Wi-Fi", device.frequency)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        device.interfaceType.isNotBlank() -> device.interfaceType
        else -> "未知连接方式"
    }

    SpeedChart(points = history, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
    DetailLine(
        label = "状态",
        value = if (device.active) "在线 · $connection" else "离线 · $connection",
        valueColor = if (device.active) UploadSpeedColor else Color.Gray,
    )
    DetailLine(label = "本次活跃", value = formatTraffic(activeDevice.activityKBytes))
    DetailLine(label = "IP", value = device.ipAddress.ifBlank { "未分配" })
    DetailLine(label = "MAC", value = device.macAddress.ifBlank { "未知" })
    DetailLine(
        label = "历史累计",
        value = "↑ ${formatTraffic(device.txKBytes)}   ↓ ${formatTraffic(device.rxKBytes)}",
    )
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
