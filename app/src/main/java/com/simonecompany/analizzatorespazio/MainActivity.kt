package com.simonecompany.analizzatorespazio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.simonecompany.analizzatorespazio.ui.navigation.AppNavHost
import com.simonecompany.analizzatorespazio.ui.theme.AnalizzatoreSpazioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnalizzatoreSpazioTheme {
                AppNavHost()
            }
        }
    }
}