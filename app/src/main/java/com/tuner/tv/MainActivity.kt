package com.tuner.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tuner.tv.ui.TunerApp
import com.tuner.tv.ui.TunerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TunerTheme {
                TunerApp()
            }
        }
    }
}
