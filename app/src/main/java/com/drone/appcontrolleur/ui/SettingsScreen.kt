package com.drone.appcontrolleur.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drone.appcontrolleur.link.LinkSettings
import com.drone.appcontrolleur.link.ProtocolType

/**
 * Paramètres (maquette Claude Design, artboard 05).
 * Seuls les réglages de liaison existent aujourd'hui ; PID, expo et trims arrivent à l'étape 11.
 * Accessible uniquement désarmé : le bouton est désactivé sur l'écran de pilotage quand le drone est armé.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: ConnectionViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()

    Column(Modifier.fillMaxSize().screenBackground()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shape = RoundedCornerShape(11.dp)
            Box(
                Modifier.size(40.dp).clip(shape).border(1.dp, DroneColors.StrokeHud, shape).clickable(onClickLabel = "Retour", onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", style = DroneType.body(20.sp, DroneColors.TextSoft, FontWeight.Bold))
            }
            Text("Paramètres", style = DroneType.display.copy(fontSize = 20.sp, letterSpacing = 0.04f.em))
            Spacer(Modifier.weight(1f))
            Pill("DÉSARMÉ · MODIFICATIONS AUTORISÉES", DroneColors.Ok, textColor = DroneColors.OkLight)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DroneColors.StrokeSoft))

        Row(
            modifier = Modifier.weight(1f).padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsCard {
                    SectionTitle("CONNEXION")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextSetting("SSID", settings.ssid, { v -> vm.updateSettings { it.copy(ssid = v) } })
                        PasswordSetting(settings.password) { v -> vm.updateSettings { it.copy(password = v) } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextSetting(
                            "IP du drone", settings.host, { v -> vm.updateSettings { it.copy(host = v.trim()) } },
                            keyboardType = KeyboardType.Uri,
                        )
                        TextSetting(
                            "Port UDP", settings.port.toString(),
                            { v -> v.toIntOrNull()?.let { p -> vm.updateSettings { it.copy(port = p) } } },
                            keyboardType = KeyboardType.Number,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Protocole", style = DroneType.body(10.sp, DroneColors.TextMuted))
                        ProtocolSelector(settings.protocol) { type -> vm.updateSettings { it.copy(protocol = type) } }
                        if (settings.protocol == ProtocolType.LEGACY_CSV) {
                            Text("Legacy CSV : tests au banc, sans hélices.", style = DroneType.body(10.sp, Color(0xFFB79459), FontWeight.Normal))
                        }
                    }
                }
                PidCard()
            }

            SettingsCard(Modifier.weight(1f).fillMaxHeight()) {
                SectionTitle("PILOTAGE")
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Zone morte, sensibilité, expo et trims seront réglables à l'étape 11.",
                        style = DroneType.body(11.sp, DroneColors.TextMuted, FontWeight.Normal),
                    )
                    Column(Modifier.alpha(0.45f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Zone morte", "Sensibilité", "Expo").forEach { SliderPlaceholder(it) }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(DroneColors.StrokeSoft))
                        Text("TRIMS", style = DroneType.caps(9.sp, tracking = 0.18f))
                        listOf("Roulis", "Tangage", "Lacet").forEach { TrimPlaceholder(it) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DroneButton("TERMINÉ", onBack, Modifier.weight(1f))
                    DroneButton("RÉINITIALISER", { vm.updateSettings { LinkSettings() } }, kind = ButtonKind.SECONDARY)
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color(0xA80E1A2A))
            .border(1.dp, DroneColors.StrokeSoft, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun SectionTitle(text: String, color: Color = DroneColors.Cyan) =
    Text(text, style = DroneType.caps(10.sp, color, FontWeight.Bold, tracking = 0.2f))

@Composable
private fun RowScope.TextSetting(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable () -> Unit = {},
) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = DroneType.body(10.sp, DroneColors.TextMuted))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = DroneType.mono(13.sp, DroneColors.TextPrimary, FontWeight.Medium),
            cursorBrush = SolidColor(DroneColors.Cyan),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            decorationBox = { field ->
                val shape = RoundedCornerShape(8.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(shape)
                        .background(Color(0x0AFFFFFF))
                        .border(1.dp, DroneColors.StrokeHud, shape)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { field() }
                    trailing()
                }
            },
        )
    }
}

@Composable
private fun RowScope.PasswordSetting(value: String, onValueChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    TextSetting(
        "Mot de passe", value, onValueChange,
        keyboardType = KeyboardType.Password,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
    ) {
        Text(
            if (visible) "MASQUER" else "AFFICHER",
            style = DroneType.caps(9.sp, DroneColors.Cyan, tracking = 0.14f),
            modifier = Modifier.clickable { visible = !visible }.padding(6.dp),
        )
    }
}

@Composable
private fun ProtocolSelector(selected: ProtocolType, onSelect: (ProtocolType) -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Row(Modifier.fillMaxWidth().height(40.dp).clip(shape).border(1.dp, DroneColors.StrokeHud, shape)) {
        ProtocolType.entries.forEachIndexed { index, type ->
            if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(DroneColors.StrokeHud))
            val isSelected = type == selected
            val accent = if (type == ProtocolType.LEGACY_CSV) DroneColors.Warn else DroneColors.Cyan
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isSelected) accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(type) },
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelected) Box(Modifier.size(5.dp).clip(CircleShape).background(accent))
                Text(
                    type.label,
                    style = DroneType.body(13.sp, if (isSelected) accent else DroneColors.TextMuted, FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
private fun PidCard() {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("PID — VERROUILLÉ", DroneColors.TextMuted)
            Spacer(Modifier.weight(1f))
            Pill("ÉTAPE 11 · FIRMWARE FW-8", DroneColors.Warn, textColor = DroneColors.WarnLight, dot = false)
        }
        Column(Modifier.alpha(0.45f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.width(60.dp))
                listOf("Kp", "Ki", "Kd").forEach {
                    Text(it, style = DroneType.caps(9.sp), modifier = Modifier.weight(1f))
                }
            }
            listOf("Roulis", "Tangage", "Lacet").forEach { axis ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(axis, style = DroneType.body(12.sp, weight = FontWeight.SemiBold), modifier = Modifier.width(60.dp))
                    repeat(3) { ValueBox("—", Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ValueBox(text: String, modifier: Modifier = Modifier, color: Color = DroneColors.TextPrimary) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier.height(32.dp).clip(shape).background(Color(0x0AFFFFFF)).border(1.dp, DroneColors.StrokeSoft, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = DroneType.mono(12.sp, color, FontWeight.Medium))
    }
}

@Composable
private fun SliderPlaceholder(label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, style = DroneType.body(12.sp), modifier = Modifier.weight(1f))
            Text("—", style = DroneType.mono(12.sp, DroneColors.Cyan))
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color(0x14FFFFFF)))
    }
}

@Composable
private fun TrimPlaceholder(axis: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(axis, style = DroneType.body(12.sp), modifier = Modifier.width(60.dp))
        ValueBox("−", Modifier.width(36.dp), DroneColors.TextSoft)
        ValueBox("0", Modifier.weight(1f), DroneColors.TextSoft)
        ValueBox("+", Modifier.width(36.dp), DroneColors.TextSoft)
    }
}
