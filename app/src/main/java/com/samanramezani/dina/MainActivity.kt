package com.samanramezani.dina
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.samanramezani.dina.ui.DinaAgentScreen
class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { DinaAgentScreen(applicationContext) } }
}
