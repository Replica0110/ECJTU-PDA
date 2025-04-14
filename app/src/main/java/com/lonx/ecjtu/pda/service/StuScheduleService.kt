package com.lonx.ecjtu.pda.service

import com.lonx.ecjtu.pda.base.BaseService
import com.lonx.ecjtu.pda.data.FullScheduleResult
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.data.StuCourse
import com.lonx.ecjtu.pda.data.TermInfo
import com.lonx.ecjtu.pda.data.WeekDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist
import timber.log.Timber
import java.io.IOException

class StuScheduleService(
    private val service: JwxtService
) : BaseService {

    class ParseException(message: String, cause: Throwable? = null) : IOException(message, cause)
    /**
     * 获取所有学期的完整课表。
     * 由于此方法会为每个学期发起一次网络请求，可能耗时较长。
     * @return ServiceResult<List<FullScheduleResult>> 包含所有成功获取的学期课表列表。
     *         如果任何学期的获取或解析失败，它们将被跳过，但操作本身可能仍返回 Success (如果至少有一个成功)。
     *         如果初始获取学期列表失败，或所有学期都获取/解析失败，则返回 Error。
     */
    suspend fun getAllSchedules(): ServiceResult<List<FullScheduleResult>> = withContext(Dispatchers.IO) {
        val allSuccessfullyParsedSchedules = mutableListOf<FullScheduleResult>()
        val failedTermDetails = mutableListOf<Pair<String, String>>()

        try {
            val initialHtmlResult = service.getScheduleHtml(term = null)
            val initialDoc: Document
            val allTermInfos: List<TermInfo>

            when (initialHtmlResult) {
                is ServiceResult.Success -> {
                    try {
                        initialDoc = Jsoup.parse(initialHtmlResult.data)
                        allTermInfos = parseTermInfoListFromDoc(initialDoc)
                        if (allTermInfos.isEmpty()) {
                            return@withContext ServiceResult.Error("No terms found in the initial schedule page.")
                        }
                        try {
                            val initialSchedule = parseScheduleFromDoc(initialDoc)
                            allSuccessfullyParsedSchedules.add(initialSchedule)
                        } catch (e: ParseException) {
                            val termValue = initialDoc.selectFirst("select#term option[selected]")?.`val`() ?: "default"
                            Timber.e("Warning: Failed to parse the initial default schedule (Term: $termValue): ${e.message}")
                            failedTermDetails.add(termValue to "Parsing initial schedule failed: ${e.message}")

                        } catch (e: Exception) {
                            val termValue = initialDoc.selectFirst("select#term option[selected]")?.`val`() ?: "default"
                            Timber.e("Warning: Unexpected error parsing the initial default schedule (Term: $termValue): ${e.message}")
                            failedTermDetails.add(termValue to "Unexpected error parsing initial schedule: ${e.message}")

                        }

                    } catch (e: Exception) {
                        Timber.e("无法解析初始 HTML 或学期列表: ${e.stackTraceToString()}")
                        return@withContext ServiceResult.Error("无法解析初始 HTML 或学期列表: ${e.message}", e)
                    }
                }
                is ServiceResult.Error -> {
                    return@withContext initialHtmlResult
                }
            }

            val initialParsedTermValue = allSuccessfullyParsedSchedules.firstOrNull()?.termValue
            for (termInfo in allTermInfos) {
                if (termInfo.value == initialParsedTermValue) {
                    continue
                }

                Timber.e("Fetching schedule for term: ${termInfo.value} (${termInfo.name})")

                when (val specificHtmlResult = service.getScheduleHtml(term = termInfo.value)) {
                    is ServiceResult.Success -> {
                        try {
                            val specificDoc = Jsoup.parse(specificHtmlResult.data)
                            val scheduleResult = parseScheduleFromDoc(specificDoc, expectedTermValue = termInfo.value)
                            allSuccessfullyParsedSchedules.add(scheduleResult)
                        } catch (e: ParseException) {
                            Timber.e("Error parsing schedule for term ${termInfo.value}: ${e.message}")
                            failedTermDetails.add(termInfo.value to "Parsing failed: ${e.message}")
                            Timber.e("Unexpected error parsing schedule for term ${termInfo.value}: ${e.stackTraceToString()}")
                            failedTermDetails.add(termInfo.value to "Unexpected parsing error: ${e.message}")
                        }
                    }
                    is ServiceResult.Error -> {
                        Timber.e("Error fetching schedule for term ${termInfo.value}: ${specificHtmlResult.message}")
                        failedTermDetails.add(termInfo.value to "Fetching failed: ${specificHtmlResult.message}")
                    }
                }
            }

            if (allSuccessfullyParsedSchedules.isNotEmpty()) {
                if (failedTermDetails.isNotEmpty()) {
                    Timber.e("Warning: Failed to fetch/parse schedules for the following terms: $failedTermDetails")
                }
                ServiceResult.Success(allSuccessfullyParsedSchedules.distinctBy { it.termValue })
            } else {
                // No schedules were successfully parsed
                ServiceResult.Error("Failed to fetch or parse any schedules. Errors encountered for terms: ${failedTermDetails.joinToString { it.first + ": " + it.second }}")
            }

        } catch (e: Exception) {
            Timber.e("Unexpected error in getAllSchedules: ${e.stackTraceToString()}")
            ServiceResult.Error("An unexpected error occurred during getAllSchedules: ${e.message}", e)
        }
    }


    /**
     * Parses a list of TermInfo objects from the <select id="term"> element in a Jsoup Document.
     * @throws ParseException if the select element or options cannot be properly parsed.
     */
    @Throws(ParseException::class)
    private fun parseTermInfoListFromDoc(doc: Document): List<TermInfo> {
        val termSelect = doc.selectFirst("select#term")
            ?: throw ParseException("Could not find term select element in document.")
        val options = termSelect.select("option")
        if (options.isEmpty()) {
            Timber.e("Warning: Found term select element, but it contains no options.")
            return emptyList()
        }
        return options.mapNotNull { option ->
            val value = option.`val`()
            val name = option.text()
            if (value.isNotEmpty() && name.isNotEmpty()) {
                TermInfo(value, name)
            } else {
                Timber.e("Warning: Skipping term option with missing value or name: ${option.outerHtml()}")
                null
            }
        }
    }

    /**
     * Parses a FullScheduleResult (term info and courses) from a Jsoup Document.
     * @param doc The Jsoup Document representing the schedule page HTML.
     * @param expectedTermValue Optional. If provided, warns if the selected term in the doc doesn't match.
     * @throws ParseException if essential elements (term select, table) are missing or parsing fails.
     */
    @Throws(ParseException::class)
    private fun parseScheduleFromDoc(doc: Document, expectedTermValue: String? = null): FullScheduleResult {
        // 1. Parse Term Info from the document
        val termSelect = doc.selectFirst("select#term")
            ?: throw ParseException("Could not find term select element in schedule document.")
        val selectedOption = termSelect.selectFirst("option[selected]")
            ?: termSelect.selectFirst("option") // Fallback if no 'selected' attribute
            ?: throw ParseException("Could not find selected term option in schedule document.")
        val actualTermValue = selectedOption.`val`()
        val actualTermName = selectedOption.text()

        // Optional validation against expected term
        if (expectedTermValue != null && actualTermValue != expectedTermValue) {
            Timber.e("Warning: Requested schedule for term '$expectedTermValue', but the loaded page shows term '$actualTermValue' selected.")
            // Decide if this is an error or just a warning. For now, just a warning.
        }

        // 2. Parse Courses from the table
        val parsedCourses = mutableListOf<StuCourse>()
        val courseTable = doc.selectFirst("table#courseSche")
            ?: throw ParseException("Could not find course table element for term $actualTermValue.")
        val rows = courseTable.select("tr:gt(0)") // Skip header row

        for (row in rows) { // Iterate through time slots (rows)
            val cells = row.select("td")
            if (cells.isEmpty()) continue

            val timeSlot = cells[0].text().trim()
            if (timeSlot.isEmpty() || !timeSlot.contains(Regex("\\d"))) continue // Skip empty or non-standard time slots

            for (dayIndex in 1 until cells.size.coerceAtMost(8)) { // Iterate through days (cells)
                val cell = cells[dayIndex]
                if (dayIndex - 1 < 0 || dayIndex - 1 >= WeekDay.entries.size) continue // Basic bounds check
                val day = WeekDay.entries[dayIndex - 1]

                // Clean HTML, keeping <br> for splitting, then replace <br> with newline
                val cellContent = Jsoup.clean(cell.html(), "", Safelist.none().addTags("br"))
                    .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                    .replace(" ", " ") // Replace non-breaking spaces
                    .trim()

                if (cellContent.isBlank()) continue // Skip empty cells

                // --- Parse potentially multiple courses within a cell ---
                val lines = cellContent.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                var i = 0
                while (i < lines.size) {
                    if (lines[i].isBlank()) { i++; continue } // Should be filtered, but double-check

                    // Assume the first line is the course name
                    val courseName = lines[i]
                    var teacher = "N/A"
                    var location = "N/A"
                    var weeksRaw = "N/A"
                    var sectionsRaw = "N/A"
                    var consumedLines = 1 // How many lines belong to this course block

                    // Find the end of the current course block
                    // Usually ends with a line containing weeks and sections info
                    var blockEndLineIndex = -1
                    for (j in i until lines.size) {
                        // Regex for "Weeks (Parity) Sections" pattern at the end of the line
                        if (Regex("""([\d\-]+(?:\s*\([单双]\))?)\s+([\d,]+)$""").containsMatchIn(lines[j])) {
                            blockEndLineIndex = j
                            break
                        }
                        // Heuristic: If the next line looks like a new course name, end the current block before it
                        if (j > i && lines[j].isNotBlank() &&
                            !lines[j].contains("@") && // Doesn't contain location marker
                            !Regex("""\d.*\d""").containsMatchIn(lines[j]) && // Doesn't look like week/section info
                            lines[j].length < 30) { // Arbitrary length limit for course names vs other info
                            blockEndLineIndex = j - 1
                            break
                        }
                    }
                    // If no clear end found, assume the last line is the end for this block
                    if (blockEndLineIndex == -1) {
                        blockEndLineIndex = lines.size - 1
                    }

                    // Extract lines for the current course block
                    val blockLines = lines.subList(i, blockEndLineIndex + 1)
                    consumedLines = blockLines.size

                    if (blockLines.isNotEmpty()) {
                        val lastLine = blockLines.last()
                        // Try to parse weeks and sections from the last line
                        val weekSecRegex = Regex("""^(.*?)\s*([\d,\-\s()单双]+)\s+([\d,]+)$""")
                        val weekSecMatch = weekSecRegex.find(lastLine)

                        if (weekSecMatch != null) {
                            // Successfully parsed weeks and sections
                            val remainingLastLine = weekSecMatch.groupValues[1].trim() // Text before weeks/sections on the last line
                            weeksRaw = weekSecMatch.groupValues[2].trim()
                            sectionsRaw = weekSecMatch.groupValues[3].trim()

                            // Gather potential teacher/location info from lines *before* the last one
                            // And also from the remaining part of the last line
                            var teacherLocString = blockLines.subList(0, blockLines.size - 1) // Exclude last line
                                .joinToString(" ")
                                .replace(courseName, "") // Avoid repeating course name
                                .trim()

                            if (remainingLastLine.isNotEmpty()) {
                                teacherLocString = if (teacherLocString.isNotEmpty()) "$teacherLocString $remainingLastLine" else remainingLastLine
                            }

                            // Extract teacher and location from the combined string
                            val locMatch = Regex("""@(.*)""").find(teacherLocString)
                            if (locMatch != null) {
                                location = locMatch.groupValues[1].trim()
                                teacher = teacherLocString.substringBeforeLast('@').trim()
                            } else {
                                teacher = teacherLocString // Assume all of it is the teacher if no '@'
                            }

                            // Create and add the course object
                            val course = StuCourse(
                                courseName = courseName, // Consider trimming courseName if teacher was part of it
                                teacher = teacher.ifEmpty { "N/A" },
                                location = location.ifEmpty { "N/A" },
                                weeksRaw = weeksRaw,
                                sectionsRaw = sectionsRaw,
                                day = day,
                                timeSlot = timeSlot
                            )
                            parsedCourses.add(course)

                        } else {
                            // Failed to parse weeks/sections from the last line - Block structure might be different
                            Timber.e("Warning: Could not parse weeks/sections from last line: '$lastLine' in block starting with '$courseName' at $day $timeSlot for term $actualTermValue. Block lines: $blockLines")
                        }
                    }
                    // Move to the next potential course block within the cell
                    i += consumedLines
                } // end while loop for lines in cell
            } // end for loop days
        }
        return FullScheduleResult(
            termValue = actualTermValue,
            termName = actualTermName,
            courses = parsedCourses
        )
    }
}