package com.makay.cleaner.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makay.cleaner.ai.SmartSuggestions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    onSuggestionClick: (SmartSuggestions.Suggestion) -> Unit = {}
) {
    val smartSuggestions = remember { SmartSuggestions(application) }
    var suggestions by remember { mutableStateOf<List<SmartSuggestions.Suggestion>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        suggestions = withContext(Dispatchers.Default) {
            smartSuggestions.getPersonalizedSuggestions()
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("💡 Akıllı Öneriler", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            suggestions.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✨", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Şu anda önerimiz yok", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Her şey yolunda görünüyor!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Kullanımınıza göre öneriler",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(suggestions, key = { it.id }) { suggestion ->
                        SuggestionCard(
                            suggestion = suggestion,
                            onClick = { onSuggestionClick(suggestion) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionCard(
    suggestion: SmartSuggestions.Suggestion,
    onClick: () -> Unit
) {
    val bg = when (suggestion.priority) {
        SmartSuggestions.Priority.URGENT -> Color(0xFFFFEBEE)
        SmartSuggestions.Priority.HIGH -> Color(0xFFFFF3E0)
        SmartSuggestions.Priority.MEDIUM -> Color(0xFFE3F2FD)
        SmartSuggestions.Priority.LOW -> Color(0xFFE8F5E9)
    }
    val accent = when (suggestion.priority) {
        SmartSuggestions.Priority.URGENT -> Color(0xFFD32F2F)
        SmartSuggestions.Priority.HIGH -> Color(0xFFF57C00)
        SmartSuggestions.Priority.MEDIUM -> Color(0xFF1976D2)
        SmartSuggestions.Priority.LOW -> Color(0xFF388E3C)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = suggestion.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = accent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when (suggestion.priority) {
                            SmartSuggestions.Priority.URGENT -> "ACİL"
                            SmartSuggestions.Priority.HIGH -> "YÜKSEK"
                            SmartSuggestions.Priority.MEDIUM -> "ORTA"
                            SmartSuggestions.Priority.LOW -> "DÜŞÜK"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = suggestion.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text("Uygula")
            }
        }
    }
}
