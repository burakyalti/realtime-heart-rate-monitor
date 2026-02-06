package net.hrapp.hr.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.hrapp.hr.R
import net.hrapp.hr.util.OemCompatibilityHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OemSetupScreen(
    onBackClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val instructions = remember { OemCompatibilityHelper.getInstructions() }
    val completedSteps = remember { mutableStateListOf<Int>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.oem_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.oem_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = instructions.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.oem_intro),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    instructions.steps.forEachIndexed { index, step ->
                        val isCompleted = completedSteps.contains(index)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            IconButton(
                                onClick = {
                                    if (isCompleted) {
                                        completedSteps.remove(index)
                                    } else {
                                        completedSteps.add(index)
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCompleted) {
                                        Icons.Filled.CheckCircle
                                    } else {
                                        Icons.Outlined.Circle
                                    },
                                    contentDescription = stringResource(
                                        if (isCompleted) R.string.oem_step_completed else R.string.oem_step_not_completed
                                    ),
                                    tint = if (isCompleted) Color(0xFF4CAF50) else Color.Gray
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = step,
                                fontSize = 14.sp,
                                color = if (isCompleted) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (instructions.settingsIntent != null) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.oem_btn_open_settings))
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                enabled = completedSteps.size == instructions.steps.size
            ) {
                Text(stringResource(R.string.oem_btn_done))
            }

            if (completedSteps.size < instructions.steps.size) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.oem_steps_remaining, instructions.steps.size - completedSteps.size),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3E0)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.oem_why_title),
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE65100)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.oem_why_message),
                        fontSize = 13.sp,
                        color = Color(0xFF5D4037)
                    )
                }
            }
        }
    }
}
