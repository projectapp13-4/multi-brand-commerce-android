package com.gurbakir.mobile.home

/** Application-owned visual policy; provider content cannot select native presentation or behavior. */
sealed interface HomeScreenPresentation {
    data object Standard : HomeScreenPresentation

    data object Gallery : HomeScreenPresentation
}
