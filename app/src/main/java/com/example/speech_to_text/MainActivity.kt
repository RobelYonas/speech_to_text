package com.example.speech_to_text

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.*
import java.util.*

class MainActivity : ComponentActivity() {

    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request microphone permission on startup
        requestMicrophonePermission()

        setContent {
            FirebaseRealtimeDatabaseUI(database) { startSpeechRecognition() }
        }
    }

    // Request microphone permissions
    private fun requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }
    }

    // Start speech recognition
    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to text")
        }

        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Activity result launcher for speech recognition
    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val recognizedText = matches?.firstOrNull() ?: "Could not recognize speech"

                // Update UI and Firebase
                SpeechManager.recognizedText = recognizedText
                processVoiceCommand(recognizedText, database)
            }
        }
}

// Singleton to store recognized text
object SpeechManager {
    var recognizedText by mutableStateOf("Press the button and speak...")
}

// Firebase UI + Speech-to-Text
@Composable
fun FirebaseRealtimeDatabaseUI(database: DatabaseReference, onSpeechStart: () -> Unit) {
    var doorState by remember { mutableStateOf("Loading...") }
    var lightState by remember { mutableStateOf("Loading...") }
    var windowState by remember { mutableStateOf("Loading...") }

    // Firebase real-time listener
    LaunchedEffect(Unit) {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                doorState = snapshot.child("door").getValue(String::class.java) ?: "Unknown"
                lightState = snapshot.child("light").getValue(String::class.java) ?: "Unknown"
                windowState = snapshot.child("window").getValue(String::class.java) ?: "Unknown"
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Realtime Database", fontSize = 24.sp, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(20.dp))

            ToggleCard("🚪 Door", doorState, doorState == "open") { database.child("door").setValue(it) }
            ToggleCard("💡 Light", lightState, lightState == "on") { database.child("light").setValue(it) }
            ToggleCard("🪟 Window", windowState, windowState == "open") { database.child("window").setValue(it) }

            Spacer(modifier = Modifier.height(20.dp))

            // Display recognized text from speech
            Text(text = "Recognized: ${SpeechManager.recognizedText}", fontSize = 20.sp)
            Spacer(modifier = Modifier.height(10.dp))

            // Speech recognition button
            Button(onClick = onSpeechStart) {
                Text("Start Voice Command")
            }
        }
    }
}

@Composable
fun ToggleCard(title: String, value: String, isChecked: Boolean, onToggle: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 20.sp, style = MaterialTheme.typography.bodyLarge)
                Text(text = value, fontSize = 18.sp, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = isChecked,
                onCheckedChange = { checked ->
                    val newState = if (checked) {
                        if (title == "💡 Light") "on" else "open"
                    } else {
                        if (title == "💡 Light") "off" else "closed"
                    }
                    onToggle(newState)
                }
            )
        }
    }
}

// Process voice commands and update Firebase
fun processVoiceCommand(command: String, database: DatabaseReference) {
    when {
        "door open" in command.lowercase() -> database.child("door").setValue("open")
        "door closed" in command.lowercase() -> database.child("door").setValue("closed")
        "light on" in command.lowercase() -> database.child("light").setValue("on")
        "light off" in command.lowercase() -> database.child("light").setValue("off")
        "window open" in command.lowercase() -> database.child("window").setValue("open")
        "window closed" in command.lowercase() -> database.child("window").setValue("closed")
    }
}
