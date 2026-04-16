package com.example.gymprogress

import android.app.Application

internal class WorkoutTemplateRepository(private val application: Application) {
    private val generalTemplates by lazy(LazyThreadSafetyMode.NONE) { builtInExercises() }
    private val handsTemplates by lazy(LazyThreadSafetyMode.NONE) { handsDayTemplates() }
    private val weightTemplatesByIdCache by lazy(LazyThreadSafetyMode.NONE) {
        (generalTemplates + handsTemplates)
            .filter { exercise -> exercise.type == ExerciseType.WEIGHTS }
            .associateBy { exercise -> exercise.id }
    }

    fun templatesForDayType(dayType: WorkoutDayType): List<ExerciseUiState> = when (dayType) {
        WorkoutDayType.GENERAL -> generalTemplates
        WorkoutDayType.HANDS -> handsTemplates
        WorkoutDayType.LEGS -> listOf(placeholderExercise(dayType))
    }

    fun weightTemplatesById(): Map<String, ExerciseUiState> = weightTemplatesByIdCache

    private fun placeholderExercise(dayType: WorkoutDayType): ExerciseUiState {
        val (id, titleRes, messageRes) = when (dayType) {
            WorkoutDayType.HANDS -> Triple(
                "hands_placeholder",
                R.string.day_placeholder_hands_title,
                R.string.day_placeholder_hands_text
            )
            WorkoutDayType.LEGS -> Triple(
                "legs_placeholder",
                R.string.day_placeholder_legs_title,
                R.string.day_placeholder_legs_text
            )
            WorkoutDayType.GENERAL -> error("General day does not use a placeholder card.")
        }
        return ExerciseUiState(
            id = id,
            name = application.getString(titleRes),
            type = ExerciseType.PLACEHOLDER,
            group = ExerciseGroup.MAIN,
            weightOptions = emptyList(),
            selectedWeight = 0,
            defaultWeight = 0,
            restBetweenSeconds = 0,
            restFinalSeconds = 0,
            totalSets = 0,
            completedSets = 0,
            hasSettings = false,
            isUnlocked = true,
            supportingText = application.getString(messageRes)
        )
    }
}

private val handsChestNextExerciseIds = listOf(
    "hands_seated_row",
    "hands_reverse_pec_deck",
    "hands_biceps_machine",
    "hands_seated_hammer_curl",
    "hands_incline_db_biceps",
    "hands_triceps_machine",
    "hands_dumbbell_french_press"
)

private val handsBackNextExerciseIds = listOf(
    "hands_chest_press",
    "hands_pec_deck",
    "hands_incline_db_press",
    "hands_biceps_machine",
    "hands_seated_hammer_curl",
    "hands_incline_db_biceps",
    "hands_triceps_machine",
    "hands_dumbbell_french_press"
)

private val handsBicepsNextExerciseIds = listOf(
    "hands_chest_press",
    "hands_pec_deck",
    "hands_incline_db_press",
    "hands_seated_row",
    "hands_reverse_pec_deck",
    "hands_triceps_machine",
    "hands_dumbbell_french_press"
)

private val handsTricepsNextExerciseIds = listOf(
    "hands_chest_press",
    "hands_pec_deck",
    "hands_incline_db_press",
    "hands_seated_row",
    "hands_reverse_pec_deck",
    "hands_biceps_machine",
    "hands_seated_hammer_curl",
    "hands_incline_db_biceps"
)

private fun handsDayTemplates(): List<ExerciseUiState> = listOf(
    handsDayGuidedExercise(
        id = "hands_warmup_shoulder_circles",
        name = "Круги плечами",
        group = ExerciseGroup.WARM_UP,
        restBetweenSeconds = 0,
        instructions = listOf(
            "Сделай 15 кругов вперед.",
            "Сделай 15 кругов назад.",
            "Двигайся мягко, без рывков и без боли."
        )
    ),
    handsDayGuidedExercise(
        id = "hands_warmup_shoulder_pendulum",
        name = "Плечевой маятник",
        group = ExerciseGroup.WARM_UP,
        restBetweenSeconds = 0,
        instructions = listOf(
            "Подними правое плечо вверх и одновременно опусти левое вниз.",
            "Потом поменяй стороны.",
            "Сделай 20 чередований в спокойном темпе."
        )
    ),
    handsDayGuidedExercise(
        id = "hands_warmup_wrist_circles",
        name = "Круги запястьями",
        group = ExerciseGroup.WARM_UP,
        restBetweenSeconds = 0,
        instructions = listOf(
            "Сделай по 10 кругов в каждую сторону.",
            "Локти держи расслабленными.",
            "Подготовь кисти без резких движений."
        )
    ),
    handsDayGuidedExercise(
        id = "hands_warmup_scissors",
        name = "Ножницы",
        group = ExerciseGroup.WARM_UP,
        restBetweenSeconds = 0,
        instructions = listOf(
            "Сядь на скамью. Разведи обе прямые руки широко в стороны на уровне плеч, затем одновременно сведи их перед собой накрест.",
            "Сделай 40 таких скрещиваний. При каждом движении чередуй руки: один раз правая рука идет поверх левой, следующий раз — левая поверх правой.",
            "Двигайся динамично, без пауз."
        )
    ),
    handsDayGuidedExercise(
        id = "hands_warmup_vertical_swings",
        name = "Вертикальные махи",
        group = ExerciseGroup.WARM_UP,
        restBetweenSeconds = 0,
        instructions = listOf(
            "Сядь на скамью. Вытяни прямые руки перед собой. Подними правую руку вверх до уровня уха, а левую одновременно опусти вниз и чуть заведи за спину.",
            "Сделай 40 махов (по 20 раз на каждую руку), меняя руки в динамичном темпе."
        )
    ),
    handsDayGuidedExercise(
        id = "hands_warmup_dynamic_elbow_flexion",
        name = "Разминка бицепса",
        group = ExerciseGroup.WARM_UP,
        restBetweenSeconds = 0,
        instructions = listOf(
            "Выполни 20 обычных сгибаний на бицепс в спокойном темпе с гантелями в 2 кг."
        )
    ),
    handsDayWeightExercise(
        id = "hands_chest_press",
        name = "Chest Press",
        weightOptions = listOf(7, 14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91),
        defaultWeight = 21,
        restBetweenSeconds = 70,
        recommendedNextExerciseIds = handsChestNextExerciseIds,
        settingsNote = """
            Отрегулируйте высоту сиденья на уровень 4 и убедитесь, что руки выстроены ровно.
        """.trimIndent()
    ),
    handsDayWeightExercise(
        id = "hands_pec_deck",
        name = "Pec Deck / Butterfly",
        weightOptions = listOf(7, 14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91),
        defaultWeight = 28,
        restBetweenSeconds = 45,
        recommendedNextExerciseIds = handsChestNextExerciseIds,
        settingsNote = PEC_DECK_SETTINGS_NOTE
    ),
    handsDayWeightExercise(
        id = "hands_incline_db_press",
        name = "Incline DB Press",
        weightOptions = listOf(3, 4, 5, 6, 7, 8, 9, 10),
        defaultWeight = 6,
        restBetweenSeconds = 70,
        recommendedNextExerciseIds = handsChestNextExerciseIds,
        settingsNote = """
            Техника:
            Установи спинку скамьи под углом 30–45° к горизонтали. Сядь, затем ляг на спинку и плотно прижми к ней спину. Ноги стоят на полу устойчиво.

            Держи гантели над верхней частью груди. Локти слегка разведены в стороны примерно на 30–60° от корпуса, предплечья направлены почти вертикально. Запястья держи прямыми.

            Опускай гантели по сторонам верхней части груди под контролем. Затем выжимай их вверх по той же траектории. В верхней точке гантели находятся над плечами, локти почти выпрямлены без жёсткой блокировки. Держи гантели стабильно и не позволяй им расходиться в стороны.
        """.trimIndent()
    ),
    ExerciseUiState(
        id = "hands_seated_row",
        name = "Seated Row",
        type = ExerciseType.WEIGHTS,
        group = ExerciseGroup.MAIN,
        weightOptions = listOf(60),
        selectedWeight = 60,
        defaultWeight = 60,
        weightLabel = "2x30кг",
        restBetweenSeconds = 45,
        restFinalSeconds = 120,
        totalSets = 3,
        completedSets = 0,
        hasSettings = false,
        recommendedNextExerciseIds = handsBackNextExerciseIds
    ),
    handsDayWeightExercise(
        id = "hands_reverse_pec_deck",
        name = "Reverse Pec Deck",
        weightOptions = listOf(7, 14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91),
        defaultWeight = 28,
        restBetweenSeconds = 45,
        recommendedNextExerciseIds = handsBackNextExerciseIds,
        settingsNote = """
            Сядь в тренажер лицом к спинке. Грудь плотно прижата, ноги расслаблены.

            Разводи рукояти назад за счет задней дельты и мышц спины, сводя лопатки. Плавно возвращай в исходное положение.
        """.trimIndent()
    ),
    handsDayWeightExercise(
        id = "hands_biceps_machine",
        name = "Biceps Machine",
        weightOptions = (1..12).toList(),
        defaultWeight = 9,
        restBetweenSeconds = 45,
        weightOptionLabelTemplate = "%d",
        recommendedNextExerciseIds = handsBicepsNextExerciseIds,
        settingsNote = """
            Сядь, упрись грудью и локтями в подушку тренажера. Сгибай руки плавно, не забрасывая вес всем телом.
        """.trimIndent()
    ),
    handsDayWeightExercise(
        id = "hands_seated_hammer_curl",
        name = "Hammer Curl",
        weightOptions = listOf(3, 4, 5, 6, 7, 8, 9, 10),
        defaultWeight = 7,
        restBetweenSeconds = 45,
        recommendedNextExerciseIds = handsBicepsNextExerciseIds,
        settingsNote = """
            Подготовка: установи спинку скамьи вертикально, примерно на 90 градусов. Сядь и прижми спину.
            Выполнение: держи гантели нейтральным хватом, ладони смотрят друг на друга. Сгибай руки к плечам. Упражнение развивает предплечья и мышцу под бицепсом.
        """.trimIndent()
    ),
    handsDayWeightExercise(
        id = "hands_incline_db_biceps",
        name = "Incline DB Biceps",
        weightOptions = listOf(3, 4, 5, 6, 7, 8, 9, 10),
        defaultWeight = 6,
        restBetweenSeconds = 45,
        recommendedNextExerciseIds = handsBicepsNextExerciseIds,
        settingsNote = """
            Подготовка: установи скамью под углом 45-60 градусов к горизонтали. Сядь и откинься на спинку. Руки с гантелями свободно свисают вниз. Плечо каждой руки всё время направлено вертикально вниз и не уходит вперед.
            Выполнение: сгибай руки именно в локтях, поднимая гантели вверх. Ладони направлены вперед и вверх, то есть используй обычный хват на бицепс. Не разворачивай плечи и не подавай локти вперед. В верхней точке бицепс напряжен, затем плавно опускай гантели вниз до полного контролируемого растяжения.
        """.trimIndent()
    ),
    handsDayWeightExercise(
        id = "hands_triceps_machine",
        name = "Triceps Machine",
        weightOptions = listOf(20, 25, 30, 35, 40, 45, 50, 55, 60),
        defaultWeight = 25,
        restBetweenSeconds = 60,
        weightOptionLabelTemplate = "2x%dкг",
        recommendedNextExerciseIds = handsTricepsNextExerciseIds,
        settingsNote = """
            Отрегулируй сиденье так, чтобы в верхней позиции локти были немного позади корпуса, а плечи оставались опущенными. Возьмись за рукояти и зафиксируй ноги под валиком, спина прямая. В стартовой точке локти согнуты примерно под 90°, предплечья почти вертикальны.

            Разгибай руки вниз, выжимая рукояти за счёт работы трицепса. Локти держи близко к корпусу, корпусом не помогай и плечи не поднимай. Внизу почти полностью выпрями руки без жёсткой блокировки в локтях, затем плавно верни рукояти вверх под контролем.
        """.trimIndent()
    ),
    handsDayWeightExercise(
        id = "hands_dumbbell_french_press",
        name = "Dumbbell French Press",
        weightOptions = listOf(3, 4, 5, 6, 7, 8, 9, 10),
        defaultWeight = 6,
        restBetweenSeconds = 60,
        recommendedNextExerciseIds = handsTricepsNextExerciseIds,
        settingsNote = """
            Сядь на скамью без поднятой спинки, держи спину ровной. Возьми одну гантель двумя руками, обхвати ладонями верхний блин и подними её над головой.

            Медленно опускай гантель за голову, сгибая локти. Верхняя часть рук остаётся почти вертикальной и неподвижной, локти направлены вверх и не расходятся сильно в стороны. Опускай гантель достаточно глубоко, но контролируй траекторию, чтобы она не задевала голову.

            Разгибай руки вверх до почти полного выпрямления и снова плавно опускай гантель. Двигай только предплечьями, корпус не раскачивай. Если появляется дискомфорт в спине, уменьши вес.
        """.trimIndent()
    ),
    ExerciseUiState(
        id = "hands_upper_body_only_rowing",
        name = "Upper Body Only Rowing",
        type = ExerciseType.ACTIVITY,
        group = ExerciseGroup.CARDIO,
        mode = "rowing",
        durationMinutes = 15,
        level = 3,
        weightOptions = emptyList(),
        selectedWeight = 0,
        defaultWeight = 0,
        restBetweenSeconds = 0,
        restFinalSeconds = 120,
        totalSets = 1,
        completedSets = 0,
        hasSettings = true,
        settingsNote = """
            Гребной тренажер без включения ног. Это упражнение дает монотонную кардио-нагрузку в целевой зоне пульса без движения в коленных суставах.

            Нагрузка: сопротивление 3-4. Время: 10-15 минут или до достижения нужного объема кардио. Целевая зона пульса: 130-140 bpm.

            Подготовка: сядь на сиденье тренажера и зафиксируй стопы в ремешках. Выпрями ноги так, чтобы коленям было комфортно. Можно оставить их слегка согнутыми, если полное выпрямление провоцирует заклинивание. Зафиксируй ноги: на протяжении всего упражнения сиденье не должно ездить по рельсе.

            Выполнение:
            1. Слегка наклони корпус вперед от бедра, держи спину прямой, а руки вытянутыми к маховику.
            2. Начни тягу: сначала немного отклони корпус назад до угла примерно 100-110 градусов.
            3. Затем мощно, но плавно потяни рукоять к низу груди или к животу, сводя лопатки вместе.
            4. Вернись в исходное положение в обратном порядке: сначала выпрями руки, затем снова наклони корпус вперед.

            Ошибки: не сгибай и не разгибай колени, не округляй поясницу при наклоне вперед и не отклоняй спину слишком далеко назад в конце тяги.
        """.trimIndent()
    ),
    handsDayGuidedExercise(
        id = "hands_cooldown_chest_stretch",
        name = "Растяжка груди",
        group = ExerciseGroup.COOLDOWN,
        restBetweenSeconds = 0,
        instructions = listOf(
            "Встань боком к стене.",
            "Прямую руку оставь на стене чуть позади линии корпуса.",
            "Плавно разворачивай грудную клетку от руки до ощущения растяжения.",
            "Удерживай 20-30 секунд (5 вдохов-выдохов) на каждую сторону."
        )
    ),
    handsDayGuidedExercise(
        id = "hands_cooldown_shoulder_stretch",
        name = "Растяжка плеч",
        group = ExerciseGroup.COOLDOWN,
        restBetweenSeconds = 0,
        instructions = listOf(
            "Прямую руку прижми к груди другой рукой.",
            "Не поднимай плечо к уху.",
            "Удерживай 20-30 секунд (5 вдохов-выдохов) на каждую сторону."
        )
    ),
    handsDayGuidedExercise(
        id = "hands_cooldown_triceps_stretch",
        name = "Растяжка трицепса",
        group = ExerciseGroup.COOLDOWN,
        restBetweenSeconds = 0,
        instructions = listOf(
            "Сядь на скамью. Подними одну руку вверх, согни в локте и заведи ладонь за голову (между лопаток).",
            "Другой рукой мягко потяни за локоть в сторону головы, усиливая растяжение.",
            "Удерживай на 20-30 медленных счетов на каждую руку."
        )
    ),
    handsDayGuidedExercise(
        id = "hands_cooldown_biceps_forearms_stretch",
        name = "Растяжка бицепса и предплечий",
        group = ExerciseGroup.COOLDOWN,
        restBetweenSeconds = 0,
        instructions = listOf(
            "Сядь на скамью. Вытяни прямую руку перед собой ладонью вверх.",
            "Другой рукой потяни пальцы вытянутой руки вниз и на себя до натяжения от запястья до локтя.",
            "Удерживай на 20-30 медленных счетов на каждую руку."
        )
    ),
    handsDayGuidedExercise(
        id = "hands_cooldown_wall_hang",
        name = "Вис у стенки",
        group = ExerciseGroup.COOLDOWN,
        restBetweenSeconds = 0,
        instructions = listOf(
            "Используй этот вариант только если он не вызывает дискомфорта в пояснице и не требует спрыгивания на ноги.",
            "Плавно потянись и повиси 20-30 секунд (5 вдохов-выдохов).",
            "Прекрати сразу, если появляется неприятное ощущение в пояснице или седалищном нерве."
        )
    )
)

private fun handsDayWeightExercise(
    id: String,
    name: String,
    weightOptions: List<Int>,
    defaultWeight: Int,
    restBetweenSeconds: Int = 45,
    weightOptionLabelTemplate: String? = null,
    recommendedNextExerciseIds: List<String> = emptyList(),
    settingsNote: String
): ExerciseUiState = ExerciseUiState(
    id = id,
    name = name,
    type = ExerciseType.WEIGHTS,
    group = ExerciseGroup.MAIN,
    weightOptions = weightOptions,
    selectedWeight = defaultWeight,
    defaultWeight = defaultWeight,
    weightOptionLabelTemplate = weightOptionLabelTemplate,
    restBetweenSeconds = restBetweenSeconds,
    restFinalSeconds = 120,
    totalSets = 3,
    completedSets = 0,
    hasSettings = true,
    settingsNote = settingsNote,
    recommendedNextExerciseIds = recommendedNextExerciseIds
)

private fun handsDayGuidedExercise(
    id: String,
    name: String,
    group: ExerciseGroup,
    restBetweenSeconds: Int,
    instructions: List<String>
): ExerciseUiState = ExerciseUiState(
    id = id,
    name = name,
    type = ExerciseType.GUIDED,
    group = group,
    weightOptions = emptyList(),
    selectedWeight = 0,
    defaultWeight = 0,
    restBetweenSeconds = restBetweenSeconds,
    restFinalSeconds = 0,
    totalSets = 1,
    completedSets = 0,
    hasSettings = false,
    detailSections = instructions
)
