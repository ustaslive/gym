package com.example.gymprogress

import android.app.Application
import android.text.Html
import java.util.Locale

internal class WorkoutShareContentBuilder(private val application: Application) {
    fun build(generalNote: String?, exercises: List<ExerciseUiState>): ShareContent? {
        val entries = mutableListOf<Pair<String, String>>()
        val normalizedGeneralNote = generalNote?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedGeneralNote != null) {
            val generalTitle = application.getString(R.string.general_note_title)
            entries += generalTitle to normalizedGeneralNote
        }
        entries += exercises.mapNotNull { exercise ->
            val name = exercise.name.trim()
            val note = exercise.personalNote?.trim()?.takeIf { it.isNotEmpty() }
            if (name.isEmpty() || note == null) {
                return@mapNotNull null
            }
            buildShareExerciseTitle(exercise, name) to note
        }
        if (entries.isEmpty()) return null

        val plainText = entries.joinToString(separator = "\n\n") { (name, note) ->
            "$name: $note"
        }
        val htmlBody = entries.joinToString(separator = "") { (name, note) ->
            "<p><strong>${Html.escapeHtml(name)}</strong>: ${escapeNoteToHtml(note)}</p>"
        }
        return ShareContent(
            plainText = plainText,
            htmlText = "<html><body>$htmlBody</body></html>"
        )
    }

    private fun buildShareExerciseTitle(
        exercise: ExerciseUiState,
        baseName: String
    ): String {
        val metadata = when (exercise.type) {
            ExerciseType.WEIGHTS -> buildWeightShareMetadata(exercise)
            ExerciseType.ACTIVITY -> buildActivityShareMetadata(exercise)
            ExerciseType.GUIDED,
            ExerciseType.PLACEHOLDER -> null
        }
        return if (metadata != null) "$baseName$metadata" else baseName
    }

    private fun buildWeightShareMetadata(exercise: ExerciseUiState): String? {
        val defaultWeight = exercise.defaultWeight.takeIf { it > 0 }
        val selectedWeight = exercise.selectedWeight.takeIf { it > 0 }
        if (defaultWeight == null && selectedWeight == null) return null

        val labelOverride = exercise.weightLabel
            ?.takeIf { it.isNotBlank() && exercise.weightOptions.size <= 1 }
        val labelTemplate = exercise.weightOptionLabelTemplate?.takeIf { it.isNotBlank() }
        val parts = listOfNotNull(
            defaultWeight?.let { weight ->
                "default=${formatShareWeight(weight, labelOverride, labelTemplate)}"
            },
            selectedWeight?.let { weight ->
                "selected=${formatShareWeight(weight, labelOverride, labelTemplate)}"
            }
        )
        if (parts.isEmpty()) return null
        return parts.joinToString(prefix = "(", postfix = ")", separator = ",")
    }

    private fun buildActivityShareMetadata(exercise: ExerciseUiState): String? =
        exercise.level?.takeIf { it > 0 }?.let { level -> "(level=$level)" }

    private fun formatShareWeight(
        weight: Int,
        labelOverride: String? = null,
        labelTemplate: String? = null
    ): String = (
        labelOverride
            ?: labelTemplate?.let { template -> String.format(Locale.getDefault(), template, weight) }
            ?: application.getString(R.string.weight_label_template, weight)
        ).replace(" ", "")

    private fun escapeNoteToHtml(note: String): String =
        note.split('\n').joinToString("<br>") { line ->
            Html.escapeHtml(line)
        }
}
