package com.echoflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Project: a durable workspace that groups conversations and gives them shared standing context.
 * Two things turn "a folder of chats" into a project — a custom instruction the model is told to
 * follow for every chat in the project, and a set of reference [ProjectDocument]s whose text is
 * handed to the model as background knowledge. Both are optional; an empty project is just a folder
 * until the user fills them in.
 *
 * A chat is linked to a project by [ChatThread.projectId]. The link is nullable and set-null on
 * delete (handled in code, not by a DB foreign key) so deleting a project never takes its
 * conversations with it — they simply return to the ordinary drawer list.
 */
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String,
    val name: String,
    /** The custom instruction prepended to the system prompt for every chat in this project. */
    val instructions: String = "",
    /** Index into the UI accent palette (see ProjectAccents) — the project's colour identity. */
    val colorIndex: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)
