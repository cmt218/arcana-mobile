package org.arcana.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arcana.mobile.theme.MossLight
import org.arcana.mobile.theme.Wood

const val SECTION_HEADING_SIZE = 18

/** A section's name at heading weight beside a rule, so a long page reads as parts and not one run of text. */
@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Heading2(
            text = title,
            size = SECTION_HEADING_SIZE,
            color = Wood,
            modifier = Modifier.opticallyCentredCapsVertical(SECTION_HEADING_SIZE.sp),
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(MossLight))
    }
}
