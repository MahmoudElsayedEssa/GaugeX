package com.binissa.gaugex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {

    // Create an OkHttpClient with GaugeX integration
    private val okHttpClient by lazy {
        GaugeX.monitorOkHttp(OkHttpClient.Builder()).build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // Initialize GaugeX with custom configuration
        if (!GaugeX.isInitialized()) {
            val config = GaugeXConfig.Builder().apiKey("demo-api-key").enableCrashReporting(true)
                .enableNetworkMonitoring(true).enablePerformanceMonitoring(true)
                .enableUserBehaviorTracking(true).enableLogCollection(true)
                .setSamplingRate("PERFORMANCE", 1.0f).setSamplingRate("NETWORK", 1.0f)
                .setSamplingRate("USER_ACTION", 1.0f).build()

            GaugeX.initialize(applicationContext, config)
            GaugeX.i("MainActivity", "GaugeX initialized with custom configuration")
        }

        // Start measuring app startup time
        GaugeX.startPerformanceMeasurement("app_startup")

        setContent {
            GaugeXDemoTheme {
                // Track screen view for the main screen
                LaunchedEffect(Unit) {
                    GaugeX.trackScreenView("MainScreen")

                    // End app startup measurement
                    delay(100) // Small delay to ensure UI is shown
                    GaugeX.endPerformanceMeasurement(
                        "app_startup", "app_launch", mapOf("launched_from" to "main_activity")
                    )
                }

                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    GaugeXDemoScreen(onMakeNetworkRequest = { makeNetworkRequest() },
                        onTriggerCrash = { triggerCrash() })
                }
            }
        }
    }

    private fun makeNetworkRequest() {
        // Use the OkHttpClient to make a network request - this will be monitored by GaugeX
        Thread {
            try {
                val request =
                    okhttp3.Request.Builder().url("https://jsonplaceholder.typicode.com/posts/1")
                        .build()

                GaugeX.startPerformanceMeasurement("network_call")
                val response = okHttpClient.newCall(request).execute()
                GaugeX.endPerformanceMeasurement(
                    "network_call", "http_request", mapOf(
                        "url" to "https://jsonplaceholder.typicode.com/posts/1",
                        "status_code" to response.code()
                    )
                )

                GaugeX.i("Network", "Network request successful: ${response.code()}")
            } catch (e: Exception) {
                GaugeX.e("Network", "Network request failed", e)
            }
        }.start()
    }

    private fun triggerCrash() {
        GaugeX.i("MainActivity", "About to trigger a test crash")
        // Wait a moment to ensure the log is processed
        Thread {
            Thread.sleep(500)
            throw RuntimeException("This is a test crash triggered by the user")
        }.start()
    }
}

@Composable
fun GaugeXDemoScreen(
    onMakeNetworkRequest: () -> Unit, onTriggerCrash: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var counterValue by remember { mutableStateOf(0) }
    var performanceMeasurements by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "GaugeX Demo App", style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Counter: $counterValue", style = MaterialTheme.typography.bodyLarge
        )

        Button(onClick = {
            counterValue++
            GaugeX.trackUserInteraction(
                "increment_counter", mapOf("counter_value" to counterValue)
            )
        }) {
            Text("Increment Counter")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            performanceMeasurements++

            coroutineScope.launch {
                GaugeX.startPerformanceMeasurement("heavy_calculation")

                // Simulate a heavy calculation
                delay(500)

                GaugeX.endPerformanceMeasurement(
                    "heavy_calculation",
                    "computation",
                    mapOf("measurement_number" to performanceMeasurements)
                )
            }
        }) {
            Text("Simulate Performance Measurement")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { onMakeNetworkRequest() }) {
            Text("Make Network Request")
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Red button to trigger crash for testing crash reporting
        Button(
            onClick = { onTriggerCrash() }, colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Test Crash Reporting")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "All user interactions, performance metrics, and screen navigation in this app are being monitored by GaugeX SDK.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun GaugeXDemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(), content = content
    )
}

@Preview(showBackground = true)
@Composable
fun GaugeXDemoScreenPreview() {
    GaugeXDemoTheme {
        GaugeXDemoScreen(onMakeNetworkRequest = {}, onTriggerCrash = {})
    }
}