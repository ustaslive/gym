package com.example.gymprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SharedExerciseBundleParserTest {
    @Test
    fun parsesCanonicalSharedBundle() {
        val bundle = listOf(
            File("../docs/data/exercise-bundle.json"),
            File("../../docs/data/exercise-bundle.json")
        ).first { it.exists() }

        val parsedBundle = parseSharedExerciseBundle(bundle.readText())

        assertTrue(parsedBundle.templatesBySessionId.getValue("android_general").isNotEmpty())
        assertTrue(parsedBundle.templatesBySessionId.getValue("android_hands").isNotEmpty())
        assertTrue(parsedBundle.sessionOptions.any { session -> session.id == "browser_gym" })
    }

    @Test
    fun parsesSessionsCatalogParametersAndOverrides() {
        val bundle = """
            {
              "schemaVersion": 1,
              "metadata": {
                "id": "test_bundle",
                "title": "Test Bundle",
                "revision": 1
              },
              "exerciseCatalog": {
                "bike": {
                  "title": "Bike",
                  "kind": "activity",
                  "parameters": {
                    "mode": "bike",
                    "durationMinutes": 10,
                    "level": 4,
                    "totalSets": 1,
                    "restFinalSeconds": 90
                  },
                  "settings": {
                    "setupNote": "Seat height 20."
                  },
                  "muscleGroups": ["cardio", "legs"]
                },
                "press": {
                  "title": "Press",
                  "kind": "weights",
                  "parameters": {
                    "weightOptions": [10, 20, 30],
                    "defaultWeight": 20,
                    "totalSets": 3,
                    "restBetweenSeconds": 45,
                    "restFinalSeconds": 120
                  },
                  "instructions": {
                    "detailSections": ["Keep posture stable."]
                  },
                  "muscleGroups": ["chest"]
                },
                "stretch": {
                  "title": "Stretch",
                  "kind": "guided",
                  "parameters": {
                    "totalSets": 1,
                    "restBetweenSeconds": 0,
                    "restFinalSeconds": 0
                  },
                  "instructions": {
                    "detailSections": ["Hold gently."]
                  }
                }
              },
              "sessions": [
                {
                  "id": "android_general",
                  "title": "General",
                  "sections": [
                    {
                      "id": "warmup",
                      "title": "Warmup",
                      "exercises": [
                        {
                          "id": "warmup_bike",
                          "exerciseId": "bike",
                          "overrides": {
                            "parameters": {
                              "durationMinutes": 15
                            }
                          }
                        }
                      ]
                    },
                    {
                      "id": "main",
                      "title": "Main",
                      "exercises": [
                        {
                          "id": "main_press",
                          "exerciseId": "press"
                        }
                      ]
                    },
                    {
                      "id": "cooldown",
                      "title": "Cooldown",
                      "exercises": [
                        {
                          "id": "cooldown_stretch",
                          "exerciseId": "stretch"
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsedBundle = parseSharedExerciseBundle(bundle)
        val exercises = parsedBundle.templatesBySessionId.getValue("android_general")

        assertEquals(
            listOf(WorkoutSessionOption(id = "android_general", title = "General")),
            parsedBundle.sessionOptions
        )

        val bike = exercises[0]
        assertEquals("warmup_bike", bike.id)
        assertEquals("Bike", bike.name)
        assertEquals(ExerciseType.ACTIVITY, bike.type)
        assertEquals(ExerciseGroup.WARM_UP, bike.group)
        assertEquals(15, bike.durationMinutes)
        assertEquals(4, bike.level)
        assertEquals("Seat height 20.", bike.settingsNote)
        assertEquals(listOf("cardio", "legs"), bike.muscleGroups)

        val press = exercises[1]
        assertEquals(ExerciseType.WEIGHTS, press.type)
        assertEquals(ExerciseGroup.MAIN, press.group)
        assertEquals(listOf(10, 20, 30), press.weightOptions)
        assertEquals(20, press.defaultWeight)
        assertEquals(20, press.selectedWeight)
        assertEquals(3, press.totalSets)
        assertEquals(listOf("chest"), press.muscleGroups)
        assertEquals(listOf("Keep posture stable."), press.detailSections)

        val stretch = exercises[2]
        assertEquals(ExerciseType.GUIDED, stretch.type)
        assertEquals(ExerciseGroup.COOLDOWN, stretch.group)
        assertTrue(stretch.detailSections.contains("Hold gently."))
    }
}
