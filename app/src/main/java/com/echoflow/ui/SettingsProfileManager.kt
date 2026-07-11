package com.echoflow.ui

import com.echoflow.data.*
import java.util.UUID

internal class SettingsProfileManager(
    private val settings: SettingsRepository,
    private val advisors: AdvisorProfileDao,
    private val panels: FusionPanelDao,
    private val agents: AgentProfileDao,
) {
    suspend fun addAdvisor(name: String, modelId: String, modelName: String) {
        val model = modelId.trim()
        if (model.isEmpty()) return
        val id = UUID.randomUUID().toString()
        advisors.insert(AdvisorProfile(id, name.trim().ifEmpty { "Advisor" }, model, modelName.trim().ifEmpty { model.substringAfterLast('/') }, System.currentTimeMillis()))
        if (settings.getEchoAdviserProfileIdDirect().isBlank()) settings.saveEchoAdviserProfileId(id)
    }

    suspend fun deleteAdvisor(id: String) {
        advisors.delete(id)
        if (settings.getEchoAdviserProfileIdDirect() == id) settings.saveEchoAdviserProfileId(advisors.getAllSync().firstOrNull()?.id.orEmpty())
    }

    suspend fun addPanel(name: String, models: List<Pair<String, String>>, judgeModelId: String?) {
        val ids = models.map { it.first.trim() }.filter(String::isNotEmpty)
        if (ids.size < 2) return
        val id = UUID.randomUUID().toString()
        panels.upsert(FusionPanel(
            id, name.trim().ifEmpty { "Panel" }, ids.joinToString("\n"),
            models.map { it.second.trim().ifEmpty { it.first.substringAfterLast('/') } }.joinToString("\n"),
            judgeModelId?.trim()?.takeIf { it.isNotEmpty() && it in ids }, System.currentTimeMillis(),
        ))
        if (settings.getEchoFusionPanelIdDirect().isBlank()) settings.saveEchoFusionPanelId(id)
    }

    suspend fun deletePanel(id: String) {
        panels.delete(id)
        if (settings.getEchoFusionPanelIdDirect() == id) settings.saveEchoFusionPanelId(panels.getAllSync().firstOrNull()?.id.orEmpty())
    }

    suspend fun addAgent(name: String, modelId: String, modelName: String, maxToolCalls: Int) {
        val model = modelId.trim()
        if (model.isEmpty()) return
        val id = UUID.randomUUID().toString()
        agents.insert(AgentProfile(id, name.trim().ifEmpty { "Agent" }, model, modelName.trim().ifEmpty { model.substringAfterLast('/') }, maxToolCalls.coerceIn(1, 25), System.currentTimeMillis()))
        if (settings.getEchoAgentProfileIdDirect().isBlank()) settings.saveEchoAgentProfileId(id)
    }

    suspend fun deleteAgent(id: String) {
        agents.delete(id)
        if (settings.getEchoAgentProfileIdDirect() == id) settings.saveEchoAgentProfileId(agents.getAllSync().firstOrNull()?.id.orEmpty())
    }
}
