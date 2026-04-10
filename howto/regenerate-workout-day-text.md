# Regenerate Workout Day Text
1. Use the requested template only as a formatting reference; do not edit it unless asked.
2. Use `/workspace/andriod/app/src/main/java/com/example/gymprogress/viewmodel/GymViewModel.kt` as the source of truth, starting from `buildExercisesForDayType(...)`.
3. Resolve the selected day to its backing list or builder such as `builtInExercises()` or `buildHandsDayTemplates()`.
4. Resolve shared notes or constants from `/workspace/andriod/app/src/main/java/com/example/gymprogress/data/ExerciseCatalog.kt` when referenced.
5. Keep the exercise order exactly as defined by the code.
6. For guided or cooldown exercises, output the name and then each instruction line in order.
7. For weight exercises, output the name, one weight line from `weightLabel` or `weightOptionLabelTemplate` or `<defaultWeight> кг`, then the settings note.
8. For activity exercises, output the name, `<durationMinutes> мин`, then the settings note.
9. If the selected day is placeholder-backed, say so and do not invent a workout; otherwise output only the regenerated plain-text block in the template's visual format.
