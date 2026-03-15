package com.nikitanikitin.localllmexp.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ondevicellm.ChatMessage
import com.example.ondevicellm.ChatViewModel
import kotlinx.coroutines.launch

// ─── Color Palette ────────────────────────────────────────────────────────────

private val BackgroundDark = Color(0xFF0D0D0F)
private val SurfaceCard = Color(0xFF1A1A1F)
private val SurfaceElevated = Color(0xFF222228)
private val BorderSubtle = Color(0xFF2E2E38)
private val AccentBlue = Color(0xFF4A90E2)
private val AccentViolet = Color(0xFF8B5CF6)
private val AccentTeal = Color(0xFF06B6D4)
private val TextPrimary = Color(0xFFF0F0F5)
private val TextSecondary = Color(0xFF8888A0)
private val TextMuted = Color(0xFF555568)

private val GradientUserBubble = Brush.linearGradient(
    colors = listOf(AccentBlue, AccentViolet),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

private val GradientHeader = Brush.linearGradient(
    colors = listOf(AccentBlue, AccentTeal),
)

// ─── Chat Screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top Bar ──────────────────────────────────────────────────────
            TopBar(
                llmReady = uiState.llmReady,
                isLoading = uiState.isLoading
            )

            // ── Messages ─────────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.messages.isEmpty() && uiState.isLoading) {
                    LoadingState()
                } else if (uiState.messages.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp,
                            top = 12.dp, bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.messages) { message ->
                            this@Column.AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(300)) + slideInVertically(
                                    tween(300, easing = EaseOutCubic)
                                ) { it / 2 }
                            ) {
                                MessageBubble(message)
                            }
                        }
                        if (uiState.isLoading) {
                            item { ThinkingIndicator() }
                        }
                    }
                }

                // Error toast
                uiState.error?.let { error ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF3D1A1A),
                        tonalElevation = 0.dp
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = Color(0xFFFF6B6B),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // ── Input Bar ─────────────────────────────────────────────────────
            ChatInputBar(
                text = inputText,
                enabled = uiState.llmReady && !uiState.isLoading,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                        coroutineScope.launch {
                            listState.animateScrollToItem(
                                maxOf(0, uiState.messages.size)
                            )
                        }
                    }
                }
            )
        }
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(llmReady: Boolean, isLoading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundDark)
            .drawBehind {
                // Bottom divider line
                drawLine(
                    color = BorderSubtle,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Animated gradient logo mark
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(AccentBlue, AccentViolet, AccentTeal)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "N",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Column {
                Text(
                    text = "On-Device LLM",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    letterSpacing = (-0.3).sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Status dot
                    val dotColor = if (llmReady) Color(0xFF34D399) else TextMuted
                    val dotAnim by rememberInfiniteTransition(label = "pulse")
                        .animateFloat(
                            initialValue = 0.5f,
                            targetValue = 1f,
                            animationSpec = if (!llmReady)  infiniteRepeatable(
                                tween(900), RepeatMode.Reverse
                            ) else infiniteRepeatable(snap(0)),
                            label = "dot_pulse"
                        )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor.copy(alpha = dotAnim))
                    )
                    Text(
                        text = when {
                            llmReady -> "Gemini Nano ready"
                            isLoading -> "Loading model…"
                            else -> "Initializing…"
                        },
                        color = if (llmReady) Color(0xFF34D399) else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }
}

// ─── Message Bubble ───────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == ChatMessage.Role.USER
    val isSystem = message.role == ChatMessage.Role.SYSTEM

    when {
        isSystem -> {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message.content,
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceCard)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        isUser -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 20.dp, topEnd = 20.dp,
                                bottomStart = 20.dp, bottomEnd = 5.dp
                            )
                        )
                        .background(GradientUserBubble)
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = message.content,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        else -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                // Small avatar for AI
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp, bottom = 2.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(AccentBlue, AccentViolet))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "G",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 5.dp, topEnd = 20.dp,
                                bottomStart = 20.dp, bottomEnd = 20.dp
                            )
                        )
                        .background(SurfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = message.content,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

// ─── Thinking Indicator ───────────────────────────────────────────────────────

@Composable
private fun ThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(AccentBlue, AccentViolet))),
            contentAlignment = Alignment.Center
        ) {
            Text("G", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Row(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 5.dp, topEnd = 20.dp,
                        bottomStart = 20.dp, bottomEnd = 20.dp
                    )
                )
                .background(SurfaceElevated)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(0, 180, 360).forEachIndexed { index, offsetMs ->
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(offsetMs)
                    ),
                    label = "dot_$index"
                )
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.7f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(offsetMs)
                    ),
                    label = "scale_$index"
                )
                Box(
                    modifier = Modifier
                        .size((8 * scale).dp)
                        .clip(CircleShape)
                        .background(AccentBlue.copy(alpha = alpha))
                )
            }
        }
    }
}

// ─── Empty / Loading States ───────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(AccentBlue, AccentViolet, AccentTeal))),
            contentAlignment = Alignment.Center
        ) {
            Text("✦", color = Color.White, fontSize = 28.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "How can I help you?",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Running entirely on your device",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun LoadingState() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "spin"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(56.dp),
                color = AccentBlue,
                trackColor = SurfaceCard,
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round
            )
            Text("✦", color = AccentViolet, fontSize = 20.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Loading Gemini Nano…",
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─── Input Bar ────────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val canSend = enabled && text.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundDark)
            .drawBehind {
                drawLine(
                    color = BorderSubtle,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Text field container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(SurfaceElevated)
                    .drawBehind {
                        // Subtle gradient border
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                listOf(
                                    BorderSubtle,
                                    if (text.isNotBlank()) AccentBlue.copy(alpha = 0.5f)
                                    else BorderSubtle
                                )
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(26.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 1.dp.toPx()
                            )
                        )
                    }
            ) {
                BasicTextField(
                    text = text,
                    enabled = enabled,
                    onTextChange = onTextChange,
                    onSend = onSend
                )
            }

            // Send button
            val sendBg = if (canSend)
                Brush.linearGradient(listOf(AccentBlue, AccentViolet))
            else
                Brush.linearGradient(listOf(SurfaceElevated, SurfaceElevated))

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(sendBg)
                    .then(
                        if (canSend) Modifier else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Send",
                        tint = if (canSend) Color.White else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BasicTextField(
    text: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    TextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "Message Gemini Nano…",
                color = TextMuted,
                fontSize = 15.sp
            )
        },
        enabled = enabled,
        maxLines = 5,
        textStyle = LocalTextStyle.current.copy(
            color = TextPrimary,
            fontSize = 15.sp,
            lineHeight = 22.sp
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = AccentBlue
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() })
    )
}