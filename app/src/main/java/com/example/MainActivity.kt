package com.example

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ConverterScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var xOffset by remember { mutableStateOf("0") }
    var yOffset by remember { mutableStateOf("32") }
    var zOffset by remember { mutableStateOf("0") }
    var seed by remember { mutableStateOf("") }
    var fillBlock by remember { mutableStateOf("1") }
    var repopulate by remember { mutableStateOf(true) }
    var extraRadius by remember { mutableStateOf("2") }
    var generatorType by remember { mutableStateOf("perlin") }
    var expanded by remember { mutableStateOf(false) }

    var isConverting by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    
    var tapCount by remember { mutableStateOf(0) }
    var previewImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var showPreviewDialog by remember { mutableStateOf(false) }

    val createSourceZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { dst ->
            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(dst)?.buffered()?.use { output ->
                            java.util.zip.ZipOutputStream(output).use { zout ->
                                val files = listOf(
                                    "com/example/MainActivity.kt",
                                    "com/example/Converter.kt",
                                    "com/example/Nbt.kt",
                                    "com/example/SimpleNoise.kt",
                                    "com/example/ui/theme/Color.kt",
                                    "com/example/ui/theme/Theme.kt",
                                    "com/example/ui/theme/Type.kt",
                                    "gradlew",
                                    "gradlew.bat",
                                    "build.gradle.kts",
                                    "settings.gradle.kts",
                                    "gradle.properties",
                                    "app/build.gradle.kts",
                                    "gradle/wrapper/gradle-wrapper.jar",
                                    "gradle/wrapper/gradle-wrapper.properties"
                                )
                                for (file in files) {
                                    try {
                                        context.assets.open(file).use { input ->
                                            zout.putNextEntry(java.util.zip.ZipEntry(file))
                                            input.copyTo(zout)
                                            zout.closeEntry()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    }
                    Toast.makeText(context, context.getString(R.string.source_code_downloaded), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.failed_prefix, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val openFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedFileUri = it }
    }

    val createZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { dst ->
            coroutineScope.launch {
                isConverting = true
                progressMessage = context.getString(R.string.preparing)

                try {
                    val config = ConverterConfig(
                        xOffset = xOffset.toIntOrNull() ?: 0,
                        yOffset = (yOffset.toIntOrNull() ?: 32).let { it - (it % 2) }, // Enforce even
                        zOffset = zOffset.toIntOrNull() ?: 0,
                        seed = seed.toLongOrNull(),
                        fillBlock = fillBlock.toIntOrNull() ?: 1,
                        repopulate = repopulate,
                        extraRadius = extraRadius.toIntOrNull() ?: 0,
                        generatorType = generatorType
                    )

                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(selectedFileUri!!)?.buffered()?.use { input ->
                            context.contentResolver.openOutputStream(dst)?.buffered()?.use { output ->
                                val res = Converter.convertToZip(input, output, config) { msg ->
                                    progressMessage = msg
                                }
                                previewImage = res.bitmap.asImageBitmap()
                            }
                        }
                    }

                    Toast.makeText(context, context.getString(R.string.conversion_success), Toast.LENGTH_LONG).show()
                    showPreviewDialog = true
                } catch (e: Throwable) {
                    Toast.makeText(context, context.getString(R.string.error_prefix, e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                } finally {
                    isConverting = false
                    progressMessage = ""
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.select_map_file), fontWeight = FontWeight.Bold)
                Button(
                    onClick = { openFileLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConverting
                ) {
                    Text(if (selectedFileUri != null) stringResource(R.string.change_mclevel_file) else stringResource(R.string.select_mclevel_file))
                }
                if (selectedFileUri != null) {
                    Text(
                        text = stringResource(R.string.selected_file, selectedFileUri?.lastPathSegment ?: stringResource(R.string.file_placeholder)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.position_offsets), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = xOffset, onValueChange = { xOffset = it },
                        label = { Text(stringResource(R.string.label_x)) }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isConverting
                    )
                    OutlinedTextField(
                        value = yOffset, onValueChange = { yOffset = it },
                        label = { Text(stringResource(R.string.label_y)) }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isConverting
                    )
                    OutlinedTextField(
                        value = zOffset, onValueChange = { zOffset = it },
                        label = { Text(stringResource(R.string.label_z)) }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isConverting
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.world_generation), fontWeight = FontWeight.Bold)
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = extraRadius, onValueChange = { extraRadius = it },
                        label = { Text(stringResource(R.string.extra_chunk_radius)) }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isConverting
                    )
                    
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = generatorType.replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            label = { Text(stringResource(R.string.terrain_type)) },
                            readOnly = true,
                            enabled = !isConverting,
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.simple_flat)) },
                                onClick = { generatorType = "flat"; expanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.perlin_noise)) },
                                onClick = { generatorType = "perlin"; expanded = false }
                            )
                        }
                        // Invisible clickable box to trigger dropdown
                        Spacer(modifier = Modifier
                            .matchParentSize()
                            .background(androidx.compose.ui.graphics.Color.Transparent)
                            .padding(8.dp)
                            .let {
                                if (!isConverting) {
                                    it.clickable { expanded = true }
                                } else it
                            }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fillBlock, onValueChange = { fillBlock = it },
                        label = { Text(stringResource(R.string.base_fill_block_id)) }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isConverting
                    )
                    OutlinedTextField(
                        value = seed, onValueChange = { seed = it },
                        label = { Text(stringResource(R.string.noise_seed_opt)) }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isConverting
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.repopulate_chunks))
                    Switch(
                        checked = repopulate,
                        onCheckedChange = { repopulate = it },
                        enabled = !isConverting
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (isConverting) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = progressMessage, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Button(
                onClick = { createZipLauncher.launch("Generated_Alpha_World.zip") },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = selectedFileUri != null
            ) {
                Text(stringResource(R.string.convert_and_generate_zip), fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp))
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.version_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable {
                    tapCount++
                    if (tapCount >= 3) {
                        tapCount = 0
                        createSourceZipLauncher.launch("SourceCode_Bugfix.zip")
                    }
                }
                .padding(8.dp)
        )

        if (showPreviewDialog && previewImage != null) {
            AlertDialog(
                onDismissRequest = { showPreviewDialog = false },
                title = { Text(stringResource(R.string.world_preview)) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            bitmap = previewImage!!,
                            contentDescription = "Map Preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(androidx.compose.ui.graphics.Color.Black),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.preview_description), style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPreviewDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }
    }
}
