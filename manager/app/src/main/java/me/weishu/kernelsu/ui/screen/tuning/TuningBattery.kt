package me.weishu.kernelsu.ui.screen.tuning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.ui.viewmodel.BatteryUiState

@Composable
fun BatteryInfoRows(
    state: BatteryUiState,
    labelColor: Color,
    valueColor: Color,
    dividerColor: Color,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (!state.supported) {
            Text(
                text = "Battery information is unavailable.",
                color = labelColor,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            return@Column
        }

        val rows = listOf(
            "Level" to state.level,
            "Health" to state.health,
            "Status" to state.status,
            "Power source" to state.powerSource,
            "Technology" to state.technology,
            "Temperature" to state.temperature,
            "Voltage" to state.voltage,
            state.currentLabel to state.currentValue,
            "Charged up to" to state.chargedUpTo,
            "Design Capacity" to state.designCapacity,
            "Kernel profile" to state.kernelProfile,
            "Model" to state.model,
            "Cycle count" to state.cycleCount,
        )

        rows.forEachIndexed { index, (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label, color = labelColor, fontSize = 15.sp)
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (index != rows.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor),
                )
            }
        }
    }
}
