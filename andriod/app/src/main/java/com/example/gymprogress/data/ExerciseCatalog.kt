package com.example.gymprogress

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.util.Locale

internal data class ExerciseAssetLoadResult(
    val exercises: List<ExerciseUiState>,
    val issueMessage: String? = null
)

private const val DEFAULT_ASSET = "exercises.json"
private const val LOG_TAG = "ExerciseCatalog"
private const val DEFAULT_REST_BETWEEN_SECONDS = 45
private const val DEFAULT_REST_FINAL_SECONDS = 120
private const val DEFAULT_ACTIVITY_REST_SECONDS = 120
private const val USER_WEIGHT_KG = 89
private const val USER_WEIGHT_LB = 196
private const val USER_AGE = 55
private const val USER_MAX_HEART_RATE = 140

internal fun loadExercisesFromAssets(context: Context): ExerciseAssetLoadResult {
    val assets = context.assets
    val raw = runCatching {
        assets.open(DEFAULT_ASSET).bufferedReader().use { it.readText() }
    }.getOrElse { error ->
        val issueMessage = context.getString(R.string.exercise_asset_issue_message, DEFAULT_ASSET)
        Log.e(LOG_TAG, "Could not read exercises asset '$DEFAULT_ASSET'. Falling back to built-in exercises.", error)
        return ExerciseAssetLoadResult(
            exercises = fallbackExercises(),
            issueMessage = issueMessage
        )
    }
    val exercises = runCatching {
        parseExercisesFromJson(raw)
    }.getOrElse { error ->
        val issueMessage = context.getString(R.string.exercise_asset_issue_message, DEFAULT_ASSET)
        Log.e(LOG_TAG, "Could not parse exercises asset '$DEFAULT_ASSET'. Falling back to built-in exercises.", error)
        return ExerciseAssetLoadResult(
            exercises = fallbackExercises(),
            issueMessage = issueMessage
        )
    }
    return ExerciseAssetLoadResult(exercises = exercises)
}

internal fun parseExercisesFromJson(raw: String): List<ExerciseUiState> {
    val jsonArray = JSONArray(raw)
    require(jsonArray.length() > 0) { "Exercises asset must not be empty." }
    val items = mutableListOf<ExerciseUiState>()
    for (index in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(index)
        val exerciseId = obj.getString("id")
        val rawType = obj.optString("type", ExerciseType.WEIGHTS.name)
        val type = runCatching { ExerciseType.valueOf(rawType.uppercase(Locale.US)) }
            .getOrElse { error ->
                throw IllegalArgumentException(
                    "Exercise '$exerciseId' has unsupported type '$rawType'.",
                    error
                )
            }
        val group = if (type == ExerciseType.COOLDOWN) {
            ExerciseGroup.COOLDOWN
        } else {
            resolveGroupForExercise(exerciseId)
        }
        when (type) {
            ExerciseType.WEIGHTS -> {
                val options = obj.optJSONArray("weightOptions")?.toIntList().orEmpty()
                require(options.isNotEmpty()) {
                    "Exercise '$exerciseId' must define a non-empty weightOptions list."
                }
                val setsCount = obj.optInt("sets", 3).coerceAtLeast(1)
                val defaultWeight = obj.optInt("defaultWeight", options.first())
                val settingsNote = obj.optString("settingsNote")
                    .takeIf { it.isNotBlank() }
                val weightLabel = obj.optString("weightLabel")
                    .takeIf { it.isNotBlank() }
                val restBetween = obj.optInt("restBetweenSeconds", DEFAULT_REST_BETWEEN_SECONDS)
                val restFinal = obj.optInt("restFinalSeconds", DEFAULT_REST_FINAL_SECONDS)
                val normalizedDefault = if (defaultWeight in options) defaultWeight else options.first()
                items += ExerciseUiState(
                    id = exerciseId,
                    name = obj.optString("label", obj.getString("id")),
                    type = ExerciseType.WEIGHTS,
                    group = group,
                    mode = obj.optString("mode").takeIf { it.isNotBlank() },
                    durationMinutes = null,
                    level = null,
                    weightOptions = options,
                    selectedWeight = normalizedDefault,
                    defaultWeight = normalizedDefault,
                    weightLabel = weightLabel,
                    restBetweenSeconds = restBetween,
                    restFinalSeconds = restFinal,
                    totalSets = setsCount,
                    completedSets = 0,
                    hasSettings = obj.optBoolean("hasSettings", false) || settingsNote != null,
                    settingsNote = settingsNote
                )
            }

            ExerciseType.ACTIVITY -> {
                val duration = obj.optInt("durationMinutes", 0).coerceAtLeast(0)
                    .takeIf { it > 0 }
                val level = obj.optInt("level", 0).coerceAtLeast(0)
                    .takeIf { it > 0 }
                val restFinal = obj.optInt("restFinalSeconds", DEFAULT_ACTIVITY_REST_SECONDS)
                    .coerceAtLeast(0)
                val settingsNote = obj.optString("settingsNote")
                    .takeIf { it.isNotBlank() }
                items += ExerciseUiState(
                    id = exerciseId,
                    name = obj.optString("label", obj.getString("id")),
                    type = ExerciseType.ACTIVITY,
                    group = group,
                    mode = obj.optString("mode").takeIf { it.isNotBlank() },
                    durationMinutes = duration,
                    level = level,
                    weightOptions = emptyList(),
                    selectedWeight = 0,
                    defaultWeight = 0,
                    restBetweenSeconds = 0,
                    restFinalSeconds = restFinal,
                    totalSets = 1,
                    completedSets = 0,
                    hasSettings = obj.optBoolean("hasSettings", false) || settingsNote != null,
                    settingsNote = settingsNote
                )
            }

            ExerciseType.COOLDOWN -> {
                val details = obj.optJSONArray("details")?.toStringList().orEmpty()
                val sets = obj.optInt("sets", 1).coerceAtLeast(1)
                items += ExerciseUiState(
                    id = exerciseId,
                    name = obj.optString("label", obj.getString("id")),
                    type = ExerciseType.COOLDOWN,
                    group = ExerciseGroup.COOLDOWN,
                    weightOptions = emptyList(),
                    selectedWeight = 0,
                    defaultWeight = 0,
                    restBetweenSeconds = 0,
                    restFinalSeconds = 0,
                    totalSets = sets,
                    completedSets = 0,
                    hasSettings = false,
                    settingsNote = null,
                    detailSections = details
                )
            }

            ExerciseType.PLACEHOLDER -> {
                throw IllegalArgumentException(
                    "Exercise '$exerciseId' uses the reserved type '$rawType'."
                )
            }
        }
    }
    require(items.isNotEmpty()) { "Exercises asset did not produce any exercises." }
    return items
}

internal fun fallbackExercises(): List<ExerciseUiState> = listOf(
    ExerciseUiState(
        id = "elliptical",
        name = "Elliptical",
        type = ExerciseType.ACTIVITY,
        group = ExerciseGroup.WARM_UP,
        mode = "elliptical",
        durationMinutes = 5,
        level = 10,
        weightOptions = emptyList(),
        selectedWeight = 0,
        defaultWeight = 0,
        restBetweenSeconds = 0,
        restFinalSeconds = DEFAULT_ACTIVITY_REST_SECONDS,
        totalSets = 1,
        completedSets = 0,
        hasSettings = true,
        settingsNote = "Вес: $USER_WEIGHT_KG кг ($USER_WEIGHT_LB lb). Возраст: $USER_AGE. Максимальный пульс: $USER_MAX_HEART_RATE. Установите уровень 10."
    ),
    ExerciseUiState(
        id = "leg_press",
        name = "Leg Press",
        type = ExerciseType.WEIGHTS,
        group = ExerciseGroup.MAIN,
        weightOptions = listOf(23, 30, 37, 44, 51, 58, 65, 72, 79, 86, 93, 100),
        selectedWeight = 51,
        defaultWeight = 51,
        restBetweenSeconds = DEFAULT_REST_BETWEEN_SECONDS,
        restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
        totalSets = 3,
        completedSets = 0,
        hasSettings = true,
        settingsNote = "Перед началом установите длину салазок на позицию 8."
    ),
    ExerciseUiState(
        id = "leg_extension",
        name = "Leg Extension",
        type = ExerciseType.WEIGHTS,
        group = ExerciseGroup.MAIN,
        weightOptions = listOf(7, 12, 14, 19, 21, 26, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91),
        selectedWeight = 21,
        defaultWeight = 21,
        restBetweenSeconds = DEFAULT_REST_BETWEEN_SECONDS,
        restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
        totalSets = 3,
        completedSets = 0,
        hasSettings = true,
        settingsNote = "Спинку зафиксируйте в пазе 5, валик для ног поставьте в положение XL."
    ),
    ExerciseUiState(
        id = "leg_curl",
        name = "Leg Curl",
        type = ExerciseType.WEIGHTS,
        group = ExerciseGroup.MAIN,
        weightOptions = listOf(7, 14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91),
        selectedWeight = 21,
        defaultWeight = 21,
        restBetweenSeconds = DEFAULT_REST_BETWEEN_SECONDS,
        restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
        totalSets = 3,
        completedSets = 0,
        hasSettings = true,
        settingsNote = "Перед упражнением установите валик для ног в положение XL."
    ),
    ExerciseUiState(
        id = "leg_abductor",
        name = "Hip abductor",
        type = ExerciseType.WEIGHTS,
        group = ExerciseGroup.MAIN,
        weightOptions = listOf(14, 21, 28, 35, 42, 49, 56, 63),
        selectedWeight = 63,
        defaultWeight = 63,
        restBetweenSeconds = DEFAULT_REST_BETWEEN_SECONDS,
        restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
        totalSets = 3,
        completedSets = 0,
        hasSettings = true,
        settingsNote = "Установите начальную ширину в положение 1."
    ),
    ExerciseUiState(
        id = "shoulder_press",
        name = "Shoulder Press",
        type = ExerciseType.WEIGHTS,
        group = ExerciseGroup.MAIN,
        weightOptions = listOf(7, 14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91),
        selectedWeight = 21,
        defaultWeight = 21,
        restBetweenSeconds = 60,
        restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
        totalSets = 3,
        completedSets = 0,
        hasSettings = true,
        settingsNote = "Отрегулируйте высоту сиденья на уровень 8."
    ),
    ExerciseUiState(
        id = "lat_pulldown",
        name = "Lat Pulldown",
        type = ExerciseType.WEIGHTS,
        group = ExerciseGroup.MAIN,
        weightOptions = listOf(7, 14, 21, 28, 35, 42, 49),
        selectedWeight = 49,
        defaultWeight = 49,
        restBetweenSeconds = 60,
        restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
        totalSets = 3,
        completedSets = 0,
        hasSettings = true,
        settingsNote = "Перед тягой установите высоту сиденья на уровень 5."
    ),
    ExerciseUiState(
        id = "chest_press",
        name = "Chest Press",
        type = ExerciseType.WEIGHTS,
        group = ExerciseGroup.MAIN,
        weightOptions = listOf(7, 14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91),
        selectedWeight = 28,
        defaultWeight = 28,
        restBetweenSeconds = DEFAULT_REST_BETWEEN_SECONDS,
        restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
        totalSets = 3,
        completedSets = 0,
        hasSettings = true,
        settingsNote = "Отрегулируйте высоту сиденья на уровень 7 и убедитесь, что руки выстроены ровно."
    ),
    ExerciseUiState(
        id = "row",
        name = "Row",
        type = ExerciseType.WEIGHTS,
        group = ExerciseGroup.MAIN,
        weightOptions = listOf(60),
        selectedWeight = 60,
        defaultWeight = 60,
        weightLabel = "2x30кг",
        restBetweenSeconds = DEFAULT_REST_BETWEEN_SECONDS,
        restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
        totalSets = 3,
        completedSets = 0,
        hasSettings = false
    ),
    ExerciseUiState(
        id = "pec_deck",
        name = "Pec Deck / Butterfly",
        type = ExerciseType.WEIGHTS,
        group = ExerciseGroup.MAIN,
        weightOptions = listOf(7, 14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91),
        selectedWeight = 35,
        defaultWeight = 35,
        restBetweenSeconds = DEFAULT_REST_BETWEEN_SECONDS,
        restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
        totalSets = 3,
        completedSets = 0,
        hasSettings = false
    ),
    ExerciseUiState(
        id = "bike",
        name = "Exercise Bike",
        type = ExerciseType.ACTIVITY,
        group = ExerciseGroup.CARDIO,
        mode = "bike",
        durationMinutes = 15,
        level = 18,
        weightOptions = emptyList(),
        selectedWeight = 0,
        defaultWeight = 0,
        restBetweenSeconds = 0,
        restFinalSeconds = DEFAULT_ACTIVITY_REST_SECONDS,
        totalSets = 1,
        completedSets = 0,
        hasSettings = true,
        settingsNote = "Вес: $USER_WEIGHT_KG кг ($USER_WEIGHT_LB lb). Возраст: $USER_AGE. Максимальный пульс: $USER_MAX_HEART_RATE. Установите уровень 18. Высота сиденья: 20."
    ),
    ExerciseUiState(
        id = "cooldown_chest_pillar",
        name = "Передняя дельта",
        type = ExerciseType.COOLDOWN,
        group = ExerciseGroup.COOLDOWN,
        weightOptions = emptyList(),
        selectedWeight = 0,
        defaultWeight = 0,
        restBetweenSeconds = 0,
        restFinalSeconds = 0,
        totalSets = 1,
        completedSets = 0,
        hasSettings = false,
        detailSections = listOf(
            "Растяжка груди у стены. Встань боком к стене.",
            "Пример: правая сторона тела рядом со стеной. Правая рука прямая, ладонь лежит на стене чуть позади линии корпуса (на 10–20°).",
            "Главное: корпус боком, рука сбоку-назад фиксирована на стене.",
            "Рука остаётся на месте, а корпус разворачиваешь в противоположную сторону — например, влево, если правая рука на стене.",
            "Поворачивай только туловище до ощущения растяжения в груди. По сути грудная клетка «отходит» от зафиксированной руки.",
            "Признак правильного выполнения: рука вытянута назад и лежит на стене, а ты вращаешь грудь от руки, чувствуя растяжение спереди плеча/груди.",
            "Сколько держать: 20–30 секунд, 1–2 раза на каждую сторону."
        )
    ),
    ExerciseUiState(
        id = "cooldown_lats",
        name = "Supported hang",
        type = ExerciseType.COOLDOWN,
        group = ExerciseGroup.COOLDOWN,
        weightOptions = emptyList(),
        selectedWeight = 0,
        defaultWeight = 0,
        restBetweenSeconds = 0,
        restFinalSeconds = 0,
        totalSets = 1,
        completedSets = 0,
        hasSettings = false,
        detailSections = listOf(
            "Широчайшие / бок корпуса у рамы.",
            "Шведская стенка, лицом к ней. Возьмись двумя руками за планку на уровне лица, уводи таз назад и вниз, вытягиваясь под собственным весом.",
            "Создай ощущение зависания на руках, растягивая корпус назад, а не падая вниз.",
            "Варианты: 20–30 секунд в среднем положении, затем при желании по 15–20 секунд с акцентом на каждую сторону.",
            "Повторить 1–2 круга."
        )
    ),
    ExerciseUiState(
        id = "cooldown_shoulders",
        name = "Плечи",
        type = ExerciseType.COOLDOWN,
        group = ExerciseGroup.COOLDOWN,
        weightOptions = emptyList(),
        selectedWeight = 0,
        defaultWeight = 0,
        restBetweenSeconds = 0,
        restFinalSeconds = 0,
        totalSets = 1,
        completedSets = 0,
        hasSettings = false,
        detailSections = listOf(
            "Задняя поверхность плеча / Cross-body shoulder stretch.",
            "Встань ровно, лицом куда угодно — положение не критично.",
            "Подними правую руку вперед на уровне груди. Заведи ее поперек груди. Левой рукой возьми правую выше локтя и мягко прижми к себе.",
            "Тянет заднюю дельту и верх спины.",
            "Сколько: по 20–30 секунд на каждую руку, 1–2 повтора."
        )
    ),
    ExerciseUiState(
        id = "cooldown_neck",
        name = "Шея",
        type = ExerciseType.COOLDOWN,
        group = ExerciseGroup.COOLDOWN,
        weightOptions = emptyList(),
        selectedWeight = 0,
        defaultWeight = 0,
        restBetweenSeconds = 0,
        restFinalSeconds = 0,
        totalSets = 1,
        completedSets = 0,
        hasSettings = false,
        detailSections = listOf(
            "Шея / верх трапеций.",
            "Встань боком к стене и прижми одно плечо и руку к поверхности.",
            "Если прижата правая рука: опусти ее вниз, держи прижатой. Левой рукой через голову мягко наклони голову влево и немного вперед.",
            "Потянет верх плеча и бок шеи.",
            "Сколько: по 15–20 секунд на каждую сторону."
        )
    ),
    ExerciseUiState(
        id = "cooldown_horizontal_leg",
        name = "Куб: гнуть нерв",
        type = ExerciseType.COOLDOWN,
        group = ExerciseGroup.COOLDOWN,
        weightOptions = emptyList(),
        selectedWeight = 0,
        defaultWeight = 0,
        restBetweenSeconds = 0,
        restFinalSeconds = 0,
        totalSets = 1,
        completedSets = 0,
        hasSettings = false,
        detailSections = listOf(
            "На высоком кубе/подоконнике до середины бедра.",
            "Для чего: отпустить наружную сторону бедра и ягодицу, разгрузить поясницу и седалищный нерв.",
            "Как: поставь правую стопу целиком на высокий стол/подоконник на уровне пояса или выше. Колено согнуто. Свободной рукой мягко дави на правое колено вниз и чуть внутрь, чтобы голень легла на поверхность. Стопа не отрывается. Корпус длинный.",
            "Сколько: по 2 подхода 20–30 секунд на каждую сторону.",
            "Подсказки: дави ладонью по бедру сразу над коленом, а не по чашечке. Если не ложится — подложи полотенце под колено.",
            "Где тянет: бок бедра и ягодица поднятой ноги, может слегка тянуть бок корпуса."
        )
    ),
    ExerciseUiState(
        id = "cooldown_box_forward",
        name = "Куб: вперед",
        type = ExerciseType.COOLDOWN,
        group = ExerciseGroup.COOLDOWN,
        weightOptions = emptyList(),
        selectedWeight = 0,
        defaultWeight = 0,
        restBetweenSeconds = 0,
        restFinalSeconds = 0,
        totalSets = 1,
        completedSets = 0,
        hasSettings = false,
        detailSections = listOf("ДАВЛЕНИЕ ВПЕРЕД.")
    ),
    ExerciseUiState(
        id = "cooldown_box_swing",
        name = "Куб: качели",
        type = ExerciseType.COOLDOWN,
        group = ExerciseGroup.COOLDOWN,
        weightOptions = emptyList(),
        selectedWeight = 0,
        defaultWeight = 0,
        restBetweenSeconds = 0,
        restFinalSeconds = 0,
        totalSets = 1,
        completedSets = 0,
        hasSettings = false,
        detailSections = listOf("КАЧЕЛИ КОЛЕНА.")
    ),
    ExerciseUiState(
        id = "cooldown_bow",
        name = "Бантик",
        type = ExerciseType.COOLDOWN,
        group = ExerciseGroup.COOLDOWN,
        weightOptions = emptyList(),
        selectedWeight = 0,
        defaultWeight = 0,
        restBetweenSeconds = 0,
        restFinalSeconds = 0,
        totalSets = 1,
        completedSets = 0,
        hasSettings = false,
        detailSections = listOf("Бантик.")
    ),
    ExerciseUiState(
        id = "cooldown_butterfly_in",
        name = "Бабочка к себе",
        type = ExerciseType.COOLDOWN,
        group = ExerciseGroup.COOLDOWN,
        weightOptions = emptyList(),
        selectedWeight = 0,
        defaultWeight = 0,
        restBetweenSeconds = 0,
        restFinalSeconds = 0,
        totalSets = 1,
        completedSets = 0,
        hasSettings = false,
        detailSections = listOf("Бабочка к себе.")
    ),
    ExerciseUiState(
        id = "cooldown_butterfly_out",
        name = "Бабочка от себя",
        type = ExerciseType.COOLDOWN,
        group = ExerciseGroup.COOLDOWN,
        weightOptions = emptyList(),
        selectedWeight = 0,
        defaultWeight = 0,
        restBetweenSeconds = 0,
        restFinalSeconds = 0,
        totalSets = 1,
        completedSets = 0,
        hasSettings = false,
        detailSections = listOf("Бабочка от себя.")
    )
)

private fun resolveGroupForExercise(exerciseId: String): ExerciseGroup =
    when (exerciseId) {
        "elliptical" -> ExerciseGroup.WARM_UP
        "bike" -> ExerciseGroup.CARDIO
        else -> ExerciseGroup.MAIN
    }

private fun JSONArray.toIntList(): List<Int> = buildList {
    for (i in 0 until length()) {
        add(getInt(i))
    }
}

private fun JSONArray.toStringList(): List<String> = buildList {
    for (i in 0 until length()) {
        val value = optString(i)
        if (value.isNotBlank()) {
            add(value)
        }
    }
}
