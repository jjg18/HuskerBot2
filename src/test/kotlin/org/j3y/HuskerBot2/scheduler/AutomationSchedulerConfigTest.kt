package org.j3y.HuskerBot2.scheduler

import com.github.kagkarlsson.scheduler.task.Task
import com.github.kagkarlsson.scheduler.task.schedule.CronSchedule
import org.j3y.HuskerBot2.automation.betting.BetProcessing
import org.j3y.HuskerBot2.automation.pickem.nfl.NflPickemProcessing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.lang.reflect.Field
import java.time.ZoneId

class AutomationSchedulerConfigTest {

    private val config = AutomationSchedulerConfig()

    @Nested
    @DisplayName("betProcessingRecurringTask")
    inner class BetProcessingTaskTests {
        @Test
        fun `returns recurring task with correct name, cron, and zone`() {
            val betProcessing = Mockito.mock(BetProcessing::class.java)

            val task: Task<Void> = config.betProcessingRecurringTask(betProcessing)
            assertNotNull(task)

            val taskName = extractTaskName(task)
            assertEquals("bet-processing", taskName)

            val schedule = extractSchedule(task)
            assertNotNull(schedule)
            assertTrue(schedule is CronSchedule, "Expected CronSchedule but got ${schedule?.javaClass}")

            val scheduleText = schedule!!.toString()
            assertTrue(scheduleText.contains("0 0 2 * * MON"), "Schedule should contain expected cron: $scheduleText")
            assertTrue(scheduleText.contains("America/Chicago"), "Schedule should contain expected zone: $scheduleText")

            Mockito.verifyNoInteractions(betProcessing)
        }

        @Test
        fun `execute handler triggers processBets`() {
            val betProcessing = Mockito.mock(BetProcessing::class.java)
            val task: Task<Void> = config.betProcessingRecurringTask(betProcessing)

            // Try to find and invoke the captured execute handler
            val invoked = invokeExecuteHandler(task)
            assertTrue(invoked, "Could not locate execute handler via reflection to invoke it")

            Mockito.verify(betProcessing, Mockito.times(1)).processBets()
        }
    }

    @Nested
    @DisplayName("nflPickemRecurringTask")
    inner class NflPickemTaskTests {
        @Test
        fun `returns recurring task with correct name, cron, and zone`() {
            val nflPickemProcessing = Mockito.mock(NflPickemProcessing::class.java)

            val task: Task<Void> = config.nflPickemRecurringTask(nflPickemProcessing)
            assertNotNull(task)

            val taskName = extractTaskName(task)
            assertEquals("nfl-pickem-post-weekly", taskName)

            val schedule = extractSchedule(task)
            assertNotNull(schedule)
            assertTrue(schedule is CronSchedule, "Expected CronSchedule but got ${schedule?.javaClass}")

            val scheduleText = schedule!!.toString()
            assertTrue(scheduleText.contains("0 0 2 * * TUE"), "Schedule should contain expected cron: $scheduleText")
            assertTrue(scheduleText.contains("America/Chicago"), "Schedule should contain expected zone: $scheduleText")

            Mockito.verifyNoInteractions(nflPickemProcessing)
        }

        @Test
        fun `execute handler triggers postWeeklyPickem`() {
            val nflPickemProcessing = Mockito.mock(NflPickemProcessing::class.java)
            val task: Task<Void> = config.nflPickemRecurringTask(nflPickemProcessing)

            val invoked = invokeExecuteHandler(task)
            assertTrue(invoked, "Could not locate execute handler via reflection to invoke it")

            Mockito.verify(nflPickemProcessing, Mockito.times(1)).postWeeklyPickem()
        }
    }

    /**
     * Utilities to introspect db-scheduler Task internals in a resilient way.
     */
    private fun extractTaskName(task: Task<Void>): String {
        // Try common approaches across db-scheduler versions
        // 1) Public method getTaskName() or getName()
        runCatching {
            val method = task.javaClass.methods.firstOrNull { (it.name == "getTaskName" || it.name == "getName") && it.parameterCount == 0 }
            if (method != null) {
                val taskNameObj = method.invoke(task)
                // TaskName may have value or toString returns the raw name
                val valueField = taskNameObj?.javaClass?.declaredFields?.firstOrNull { it.name == "value" }
                if (valueField != null) {
                    valueField.isAccessible = true
                    val value = valueField.get(taskNameObj) as? String
                    if (value != null) return value
                }
                return taskNameObj.toString()
            }
        }
        // 2) Private field 'name' or 'taskName'
        runCatching {
            val nameField = task.javaClass.declaredFields.firstOrNull { it.name == "name" || it.name == "taskName" }
            if (nameField != null) {
                nameField.isAccessible = true
                val taskNameObj = nameField.get(task)
                // Try to resolve to raw string
                val valueField = taskNameObj?.javaClass?.declaredFields?.firstOrNull { it.name == "value" }
                if (valueField != null) {
                    valueField.isAccessible = true
                    val value = valueField.get(taskNameObj) as? String
                    if (value != null) return value
                }
                return taskNameObj.toString()
            }
        }
        // 3) Try to create a TaskInstance via instance(...) and read its taskName
        runCatching {
            val instanceMethod = task.javaClass.methods.firstOrNull { it.name == "instance" }
            if (instanceMethod != null) {
                val params = instanceMethod.parameterTypes
                val args: Array<Any?> = when (params.size) {
                    1 -> arrayOf("test-id")
                    2 -> arrayOf("test-id", null)
                    3 -> arrayOf("test-id", null, null)
                    else -> arrayOf("test-id")
                }
                val instanceObj = instanceMethod.invoke(task, *args)
                // Try taskName field on instance
                val tnField = instanceObj?.javaClass?.declaredFields?.firstOrNull { it.name == "taskName" || it.name == "name" }
                if (tnField != null) {
                    tnField.isAccessible = true
                    val taskNameObj = tnField.get(instanceObj)
                    val valueField = taskNameObj?.javaClass?.declaredFields?.firstOrNull { it.name == "value" }
                    if (valueField != null) {
                        valueField.isAccessible = true
                        val value = valueField.get(taskNameObj) as? String
                        if (value != null) return value
                    }
                    return taskNameObj.toString()
                }
                // As a fallback, instance toString may contain the name
                val text = instanceObj.toString()
                if (text.isNotBlank()) return text
            }
        }
        // 4) Last resort: parse toString if it embeds the name
        val text = task.toString()
        val marker = "name="
        val idx = text.indexOf(marker)
        if (idx >= 0) {
            val after = text.substring(idx + marker.length)
            val end = after.indexOf(',')
            return if (end >= 0) after.substring(0, end).trim() else after.trim()
        }
        return text
    }

    private fun invokeExecuteHandler(task: Task<Void>): Boolean {
        // Search for a field holding a handler with a method 'execute(TaskInstance, ExecutionContext)'
        val visited = mutableSetOf<Any>()
        fun tryInvoke(obj: Any?): Boolean {
            if (obj == null) return false
            if (!visited.add(obj)) return false
            // Try to find an execute method with two parameters
            val exec = obj.javaClass.methods.firstOrNull { it.name == "execute" && it.parameterCount == 2 }
            if (exec != null) {
                runCatching {
                    exec.isAccessible = true
                    exec.invoke(obj, null, null)
                    return true
                }
            }
            // Recurse into fields
            obj.javaClass.declaredFields.forEach { f ->
                runCatching {
                    f.isAccessible = true
                    val v = f.get(obj)
                    if (tryInvoke(v)) return true
                }
            }
            return false
        }
        // Start from the task object and its fields
        if (tryInvoke(task)) return true
        task.javaClass.declaredFields.forEach { f ->
            runCatching {
                f.isAccessible = true
                val v = f.get(task)
                if (tryInvoke(v)) return true
            }
        }
        return false
    }

    private fun extractSchedule(task: Task<Void>): Any? {
        // Most RecurringTask implementations keep a 'schedule' field
        val field = task.javaClass.declaredFields.firstOrNull { it.name == "schedule" }
            ?: // sometimes it is kept on a delegate/inner class field
            task.javaClass.declaredFields.firstOrNull { it.name.contains("recurring", ignoreCase = true) }
        if (field != null) {
            field.isAccessible = true
            val candidate = field.get(task)
            if (candidate is CronSchedule) return candidate
            // If it's a wrapper, try to find 'schedule' inside it
            val inner = candidate?.javaClass?.declaredFields?.firstOrNull { it.name == "schedule" }
            if (inner != null) {
                inner.isAccessible = true
                return inner.get(candidate)
            }
            return candidate
        }
        // Try superclass fields
        val superField = task.javaClass.superclass?.declaredFields?.firstOrNull { it.name == "schedule" }
        if (superField != null) {
            superField.isAccessible = true
            return superField.get(task)
        }
        return null
    }

    private fun <T> extractField(instance: Any, fieldName: String): T {
        val field: Field = findField(instance.javaClass, fieldName)
            ?: throw AssertionError("Field '$fieldName' not found on ${instance.javaClass}")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(instance) as T
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            val match = current.declaredFields.firstOrNull { it.name == fieldName }
            if (match != null) return match
            current = current.superclass
        }
        return null
    }
}