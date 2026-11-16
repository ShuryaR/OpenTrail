package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable
    data object Login : AppRoute

    @Serializable
    data object PermissionWizard : AppRoute

    @Serializable
    data object Home : AppRoute

    @Serializable
    data class RecordDetail(
        val doctype: String,
        val docname: String
    ) : AppRoute

    @Serializable
    data object SyncAndSettings : AppRoute
}

enum class HomeTab(val label: String) {
    TASKS("Tasks"),
    LEADS("Leads"),
    DEALS("Deals"),
    CONTACTS("Contacts")
}
