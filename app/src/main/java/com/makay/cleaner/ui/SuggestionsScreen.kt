package com.makay.cleaner.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.45f
    val bg = if (isLight) Color(0xFFF4F4F6) else Color(0xFF000000)
    val card = if (isLight) Color(0xFFFFFFFF) else Color(0xFF2C2C2E)

    LaunchedEffect(Unit) {
        isLoading = true
        suggestions = withContext(Dispatchers.Default) {
            smartSuggestions.getPersonalizedSuggestions()
        }
        isLoading = false
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = {
                    Text("Akıllı öneriler", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg)
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
                        Text(
                            "Şu anda öneri yok",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Cihaz bakımı iyi görünüyor",
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
                            text = "Kullanımınıza göre öncelikli adımlar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(suggestions, key = { it.id }) { suggestion ->
                        SuggestionCard(
                            suggestion = suggestion,
                            cardColor = card,
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
    cardColor: Color = MaterialTheme.colorScheme.surface,
    onClick: () -> Unit
) {
    val accent = when (suggestion.priority) {
        SmartSuggestions.Priority.URGENT -> Color(0xFFFF453A)
        SmartSuggestions.Priority.HIGH -> Color(0xFFFF9F0A)
        SmartSuggestions.Priority.MEDIUM -> Color(0xFF3F8CFF)
        SmartSuggestions.Priority.LOW -> Color(0xFF30D158)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        onClick = onClick
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text("Uygula", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
