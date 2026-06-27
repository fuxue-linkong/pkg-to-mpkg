package com.example.pkg2mpkg

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.example.pkg2mpkg.ui.theme.Pkg2MpkgTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Pkg2MpkgTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConverterScreen()
                }
            }
        }
    }
}

@Composable
fun ConverterScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var analysis by remember { mutableStateOf<PkgConverter.Analysis?>(null) }
    var result by remember { mutableStateOf<PkgConverter.Result?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedUri = it
            fileName = DocumentFile.fromSingleUri(context, it)?.name ?: it.lastPathSegment ?: "unknown"
            result = null
            error = null
            analysis = null
            scope.launch {
                isWorking = true
                try {
                    val pkg = context.contentResolver.openInputStream(it)?.use { stream ->
                        PkgFormat.read(stream)
                    } ?: throw PkgFormat.PkgException("无法读取文件")
                    analysis = PkgConverter.analyze(pkg)
                } catch (e: Exception) {
                    error = "解析失败：${e.message}"
                    selectedUri = null
                } finally {
                    isWorking = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "PKG → MPKG 转换器",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "选择 Wallpaper Engine 桌面版的 .pkg 文件，本工具会尝试重新打包为 .mpkg。",
            style = MaterialTheme.typography.bodyMedium
        )

        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isWorking
        ) {
            Text(text = if (selectedUri == null) "选择 PKG 文件" else "重新选择")
        }

        selectedUri?.let {
            Text(
                text = "已选：$fileName",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        if (isWorking) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator()
                Text("处理中…")
            }
        }

        error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    text = it,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        analysis?.let { a ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("文件分析", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("类型：${a.type.name}")
                    Text("Magic：${a.magic}")
                    Text("条目数：${a.entryCount}")
                    a.projectTitle?.let { Text("标题：$it") }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(a.typeReason, style = MaterialTheme.typography.bodySmall)
                }
            }

            val inputUri = selectedUri
            Button(
                onClick = {
                    inputUri?.let { uri ->
                        scope.launch {
                            isWorking = true
                            result = null
                            result = PkgConverter.convert(context, uri, fileName)
                            isWorking = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = analysis?.canTryConvert == true && !isWorking && inputUri != null
            ) {
                Text("转换为 MPKG")
            }
        }

        result?.let { r ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (r.success)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (r.success) "转换成功" else "转换失败",
                        fontWeight = FontWeight.Bold,
                        color = if (r.success)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = r.message,
                        color = if (r.success)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )

                    if (r.success && r.outputUri != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { shareMpkg(context, r.outputUri) }
                            ) {
                                Text("分享到 Wallpaper Engine")
                            }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("重要说明", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• 视频/图片类壁纸：直接重打包通常可用。\n" +
                            "• 场景（Scene）类壁纸：必须在 PC 版 Wallpaper Engine 中渲染为视频后才能生成真正的 MPKG，本工具只能重新打包原文件。\n" +
                            "• 网页/应用程序类壁纸：安卓 Wallpaper Engine 可能不支持。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun shareMpkg(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "用 Wallpaper Engine 导入")
    context.startActivity(chooser)
}
