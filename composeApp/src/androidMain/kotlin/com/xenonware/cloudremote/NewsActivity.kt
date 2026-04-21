package com.xenonware.cloudremote

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xenon.mylibrary.theme.QuicksandTitleVariable
import com.xenonware.cloudremote.data.SharedPreferenceManager
import com.xenonware.cloudremote.ui.res.AnimatedGradientBackground
import com.xenonware.cloudremote.ui.theme.XenonTheme
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

@Serializable
data class ChangelogItem(
    @SerialName("versionCode") val versionCode: Int,
    @SerialName("versionName") val versionName: String? = null,
    @SerialName("changes") val changes: Map<String, List<String>>
)

class NewsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedPreferenceManager = SharedPreferenceManager(this)

        setContent {
            XenonTheme(darkTheme = isSystemInDarkTheme()) {
                AnimatedGradientBackground {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        WhatsNewScreen(onFinish = {
                            sharedPreferenceManager.lastSeenVersionName = BuildConfig.VERSION_NAME
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun WhatsNewScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val sharedPreferenceManager = remember { SharedPreferenceManager(context) }
    
    val changelog = remember {
        try {
            val jsonString = context.assets.open("changelog.json").bufferedReader().use { it.readText() }
            Log.d("NewsActivity", "JSON Content: $jsonString")
            
            val json = Json { 
                ignoreUnknownKeys = true 
                coerceInputValues = true
            }
            val items = json.decodeFromString<List<ChangelogItem>>(jsonString)
            
            Log.d("NewsActivity", "Parsed items count: ${items.size}")
            Log.d("NewsActivity", "lastSeenVersionName: ${sharedPreferenceManager.lastSeenVersionName}")
            Log.d("NewsActivity", "BuildConfig.VERSION_NAME: ${BuildConfig.VERSION_NAME}")

            val filteredItems = items.filter { it.versionName != sharedPreferenceManager.lastSeenVersionName || (it.versionName == BuildConfig.VERSION_NAME && sharedPreferenceManager.lastSeenVersionName != BuildConfig.VERSION_NAME) }
            Log.d("NewsActivity", "Filtered items count: ${filteredItems.size}")
            
            if (filteredItems.isEmpty()) {
                Log.d("NewsActivity", "No new items to show, redirecting to MainActivity")
                sharedPreferenceManager.lastSeenVersionName = BuildConfig.VERSION_NAME
                context.startActivity(Intent(context, MainActivity::class.java))
                (context as? ComponentActivity)?.finish()
            }

            filteredItems.sortedByDescending { it.versionCode }
        } catch (e: Exception) {
            Log.e("NewsActivity", "Error loading changelog: ${e.message}", e)
            emptyList()
        }
    }

    val configuration = LocalConfiguration.current
    val currentLanguage = remember(configuration) {
        val savedTag = sharedPreferenceManager.languageTag
        Log.d("NewsActivity", "Saved language tag: $savedTag")
        if (savedTag.isNotEmpty()) {
            Locale.forLanguageTag(savedTag).language
        } else {
            configuration.locales[0].language
        }
    }
    Log.d("NewsActivity", "Current language detected: $currentLanguage")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = stringResource(R.string.news_title),
                style = MaterialTheme.typography.headlineLarge.copy(
                    hyphens = Hyphens.Auto,
                    lineBreak = LineBreak.Paragraph,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.25f),
                        offset = Offset(x = 2f, y = 4f),
                        blurRadius = 8f
                    )
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontFamily = QuicksandTitleVariable,
            )
            Spacer(modifier = Modifier.height(16.dp))

            changelog.forEach { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    val versionChanges = item.changes[currentLanguage] ?: item.changes["en"] ?: emptyList()
                    Log.d("NewsActivity", "Displaying version: ${item.versionCode}, changes count: ${versionChanges.size}, lang: $currentLanguage")

                    val packageManager = context.packageManager
                    val packageName = context.packageName
                    val packageInfo = remember {
                        try {
                            packageManager.getPackageInfo(packageName, 0)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    val appVersion = packageInfo?.versionName ?: "N/A"


                    Text(
                        text = "Version $appVersion",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            hyphens = Hyphens.Auto,
                            lineBreak = LineBreak.Paragraph,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.5f),
                                offset = Offset(x = 1f, y = 2f),
                                blurRadius = 2f
                            )
                        ), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    versionChanges.forEach { change ->
                        Text(
                            text = "• $change",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                hyphens = Hyphens.Auto,
                                lineBreak = LineBreak.Paragraph,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    offset = Offset(x = 1f, y = 2f),
                                    blurRadius = 2f
                                )
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                        )
                    }
                }
            }
        }

        Button(
            onClick = onFinish,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface
            ), modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .height(96.dp)
        ) {
            Text(
                text = stringResource(R.string.finish),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = QuicksandTitleVariable,
            )
        }
    }
}
