package com.gurbakir.mobile

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction

fun SemanticsNodeInteraction.performDeterministicClick(): SemanticsNodeInteraction =
    performSemanticsAction(SemanticsActions.OnClick)
