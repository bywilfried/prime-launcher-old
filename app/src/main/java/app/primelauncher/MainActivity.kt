package app.primelauncher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PrimeLauncherApp(packageManager) }
    }
}

data class LaunchableApp(val label: String, val component: ComponentName, val icon: ImageBitmap)

@Composable
fun PrimeLauncherApp(pm: PackageManager) {
    val context = LocalContext.current
    var drawerOpen by remember { mutableStateOf(false) }
    val apps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0).map { info ->
            LaunchableApp(
                info.loadLabel(pm).toString(),
                ComponentName(info.activityInfo.packageName, info.activityInfo.name),
                info.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
            )
        }.sortedBy { it.label.lowercase() }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
            if (drawerOpen) {
                AppDrawer(apps, { drawerOpen = false }) { app ->
                    val launchIntent = Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(app.component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(launchIntent) }
                }
            } else {
                HomeScreen { drawerOpen = true }
            }
        }
    }
}

@Composable
private fun HomeScreen(openDrawer: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Text(
            "Prime Launcher",
            Modifier.align(Alignment.TopStart).padding(24.dp),
            style = MaterialTheme.typography.titleLarge
        )
        FilledTonalButton(
            onClick = openDrawer,
            modifier = Modifier.align(Alignment.BottomCenter).padding(28.dp)
        ) {
            Text("Applications")
        }
    }
}

@Composable
private fun AppDrawer(
    apps: List<LaunchableApp>,
    close: () -> Unit,
    launch: (LaunchableApp) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = close) { Text("Accueil") }
            Spacer(Modifier.width(12.dp))
            Text("Toutes les applications", style = MaterialTheme.typography.titleLarge)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(72.dp),
            contentPadding = PaddingValues(12.dp)
        ) {
            items(apps, key = { it.component.flattenToString() }) { app ->
                Column(
                    Modifier.padding(8.dp).clickable { launch(app) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(app.icon, contentDescription = app.label, modifier = Modifier.size(52.dp))
                    Text(app.label, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
