package com.example.gymprogress

import org.junit.Assert.assertThrows
import org.junit.Test

class ExerciseAssetParsingTest {
    @Test
    fun parseExercisesFromJsonThrowsOnEmptyAsset() {
        assertThrows(IllegalArgumentException::class.java) {
            GymViewModel.parseExercisesFromJson("[]")
        }
    }

    @Test
    fun parseExercisesFromJsonThrowsOnUnknownType() {
        assertThrows(IllegalArgumentException::class.java) {
            GymViewModel.parseExercisesFromJson(
                """
                [
                  {
                    "id": "broken",
                    "type": "mystery",
                    "label": "Broken"
                  }
                ]
                """.trimIndent()
            )
        }
    }

    @Test
    fun parseExercisesFromJsonThrowsOnMissingWeightOptions() {
        assertThrows(IllegalArgumentException::class.java) {
            GymViewModel.parseExercisesFromJson(
                """
                [
                  {
                    "id": "leg_press",
                    "type": "weights",
                    "label": "Leg Press",
                    "weightOptions": []
                  }
                ]
                """.trimIndent()
            )
        }
    }
}
