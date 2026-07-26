package com.echoflow.ui

import com.echoflow.data.GeneratedVideo
import com.echoflow.data.StreamChunk

/** Applies provider stream events to the ordered visual reply timeline. */
internal object ChatSegmentReducer {
    fun reduce(segments: MutableList<StreamSegment>, chunk: StreamChunk): String? {
        // The "deploying agents" banner is only a waiting indicator: clear it the moment any real
        // reply content (a delegation, reasoning, the answer, …) starts to arrive.
        if (chunk !is StreamChunk.AgentRunStarted && chunk !is StreamChunk.StatusNote) {
            segments.removeAll { it is StreamSegment.AgentRun }
        }
        when (chunk) {
            is StreamChunk.AgentRunStarted -> {
                if (segments.none { it is StreamSegment.AgentRun }) segments.add(StreamSegment.AgentRun)
            }
            is StreamChunk.Reasoning -> {
                val last = segments.lastOrNull()
                if (last is StreamSegment.Reasoning) {
                    segments[segments.lastIndex] = last.copy(text = last.text + chunk.text)
                } else {
                    segments.add(StreamSegment.Reasoning(chunk.text))
                }
            }
            is StreamChunk.Content -> {
                val last = segments.lastOrNull()
                if (last is StreamSegment.Text) {
                    segments[segments.lastIndex] = last.copy(text = last.text + chunk.text)
                } else {
                    // A Text block after a Search block is what creates the t3-style
                    // "searched → wrote → searched again" interleave.
                    segments.add(StreamSegment.Text(chunk.text))
                }
            }
            is StreamChunk.SearchStarted -> {
                segments.add(StreamSegment.Search(chunk.query, emptyList(), active = true))
            }
            is StreamChunk.SearchSources -> {
                val activeIdx = segments.indexOfLast { it is StreamSegment.Search && it.active }
                if (activeIdx >= 0) {
                    val seg = segments[activeIdx] as StreamSegment.Search
                    segments[activeIdx] = seg.copy(sources = seg.sources + chunk.sources, active = false)
                } else {
                    // Sources without a started search (e.g. server-tool annotations whose
                    // tool-call delta shape we didn't recognize) still get a card.
                    segments.add(StreamSegment.Search(chunk.query, chunk.sources, active = false))
                }
            }
            is StreamChunk.StatusNote -> {
                return chunk.text
            }
            is StreamChunk.AdvisorStarted -> {
                segments.add(
                    StreamSegment.Advisor(
                        advisorName = chunk.advisorName,
                        advisorModel = chunk.advisorModel,
                        prompt = chunk.prompt,
                        advice = null,
                        active = true,
                    )
                )
            }
            is StreamChunk.AdvisorResolved -> {
                val idx = segments.indexOfLast { it is StreamSegment.Advisor && it.active }
                if (idx >= 0) {
                    val seg = segments[idx] as StreamSegment.Advisor
                    segments[idx] = seg.copy(
                        advisorModel = chunk.advice.advisorModel.ifBlank { seg.advisorModel },
                        prompt = seg.prompt.ifBlank { chunk.advice.prompt },
                        advice = chunk.advice.advice,
                        active = false,
                    )
                } else {
                    segments.add(
                        StreamSegment.Advisor(
                            advisorName = chunk.advice.advisorName,
                            advisorModel = chunk.advice.advisorModel,
                            prompt = chunk.advice.prompt,
                            advice = chunk.advice.advice,
                            active = false,
                        )
                    )
                }
            }
            is StreamChunk.FusionStarted -> {
                segments.add(
                    StreamSegment.Fusion(
                        panelName = chunk.panelName,
                        models = chunk.models,
                        analysis = null,
                        active = true,
                    )
                )
            }
            is StreamChunk.FusionResolved -> {
                val idx = segments.indexOfLast { it is StreamSegment.Fusion && it.active }
                if (idx >= 0) {
                    val seg = segments[idx] as StreamSegment.Fusion
                    segments[idx] = seg.copy(
                        models = seg.models.ifEmpty { chunk.analysis.models },
                        analysis = chunk.analysis,
                        active = false,
                    )
                } else {
                    segments.add(
                        StreamSegment.Fusion(
                            panelName = chunk.analysis.panelName,
                            models = chunk.analysis.models,
                            analysis = chunk.analysis,
                            active = false,
                        )
                    )
                }
            }
            is StreamChunk.SubagentStarted -> {
                segments.add(
                    StreamSegment.Subagent(
                        taskName = chunk.taskName,
                        taskDescription = chunk.taskDescription,
                        workerModel = chunk.workerModel,
                        outcome = null,
                        error = null,
                        active = true,
                    )
                )
            }
            is StreamChunk.SubagentResolved -> {
                val r = chunk.result
                // Match the running delegation by task name; fall back to the last active one.
                val idx = segments.indexOfLast {
                    it is StreamSegment.Subagent && it.active && (it.taskName == r.taskName || r.taskName.isBlank())
                }.takeIf { it >= 0 } ?: segments.indexOfLast { it is StreamSegment.Subagent && it.active }
                if (idx >= 0) {
                    val seg = segments[idx] as StreamSegment.Subagent
                    segments[idx] = seg.copy(
                        workerModel = r.workerModel.ifBlank { seg.workerModel },
                        outcome = r.outcome,
                        error = r.error,
                        active = false,
                    )
                } else {
                    segments.add(
                        StreamSegment.Subagent(
                            taskName = r.taskName,
                            taskDescription = r.taskDescription,
                            workerModel = r.workerModel,
                            outcome = r.outcome,
                            error = r.error,
                            active = false,
                        )
                    )
                }
            }
            is StreamChunk.ArtifactStarted -> {
                segments.add(
                    StreamSegment.Artifact(
                        artifactId = null,
                        title = chunk.title,
                        artifactType = chunk.artifactType,
                        version = 0,
                        charCount = 0,
                        building = true,
                        truncated = false,
                    )
                )
            }
            is StreamChunk.ArtifactProgress -> {
                val idx = segments.indexOfLast { it is StreamSegment.Artifact && it.building }
                if (idx >= 0) {
                    val seg = segments[idx] as StreamSegment.Artifact
                    segments[idx] = seg.copy(charCount = chunk.charCount)
                }
            }
            is StreamChunk.ImageGenStarted -> {
                segments.add(
                    StreamSegment.Image(
                        imageId = null,
                        filePath = null,
                        pattern = chunk.pattern,
                        editing = chunk.editing,
                        previousImagePath = chunk.previousImagePath,
                        generating = true,
                    )
                )
            }
            is StreamChunk.ImageGenerated -> {
                // Only chunks the ViewModel already persisted (filePath set) reach the timeline;
                // a raw base64 chunk that slipped through is ignored rather than rendered.
                val path = chunk.filePath ?: return null
                val idx = segments.indexOfLast { it is StreamSegment.Image && it.generating }
                val done = if (idx >= 0) {
                    (segments[idx] as StreamSegment.Image).copy(
                        imageId = chunk.imageId,
                        filePath = path,
                        generating = false,
                    )
                } else {
                    StreamSegment.Image(
                        imageId = chunk.imageId,
                        filePath = path,
                        pattern = "ripple",
                        editing = false,
                        previousImagePath = null,
                        generating = false,
                    )
                }
                if (idx >= 0) segments[idx] = done else segments.add(done)
            }
            is StreamChunk.VideoGenStarted -> {
                segments.add(
                    StreamSegment.Video(
                        videoId = chunk.videoId,
                        filePath = null,
                        pattern = chunk.pattern,
                        aspectRatio = chunk.aspectRatio,
                        status = GeneratedVideo.STATUS_QUEUED,
                        generating = true,
                    )
                )
            }
            is StreamChunk.VideoGenProgress -> {
                val idx = segments.indexOfLast { it is StreamSegment.Video && it.videoId == chunk.videoId }
                if (idx >= 0) {
                    segments[idx] = (segments[idx] as StreamSegment.Video)
                        .copy(status = chunk.status, error = chunk.error)
                }
            }
            is StreamChunk.VideoGenerated -> {
                val idx = segments.indexOfLast { it is StreamSegment.Video && it.videoId == chunk.videoId }
                if (idx >= 0) {
                    segments[idx] = (segments[idx] as StreamSegment.Video).copy(
                        filePath = chunk.filePath,
                        status = GeneratedVideo.STATUS_COMPLETED,
                        generating = false,
                    )
                }
            }
            is StreamChunk.ArtifactCompleted -> {
                val idx = segments.indexOfLast { it is StreamSegment.Artifact && it.building }
                val finished = StreamSegment.Artifact(
                    artifactId = chunk.artifactId,
                    title = chunk.title,
                    artifactType = chunk.artifactType,
                    version = chunk.version,
                    charCount = chunk.content.length,
                    building = false,
                    truncated = chunk.truncated,
                )
                if (idx >= 0) segments[idx] = finished else segments.add(finished)
            }
        }
        return null
    }

}
