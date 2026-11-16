package com.example

import com.example.data.local.TaskEntity
import com.example.ui.screens.TaskBucket
import com.example.ui.screens.bucketTasks
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskBucketTest {
    private fun task(name: String, due: String, done: Boolean = false) = TaskEntity(
        name = name, title = name, description = "", status = "Todo", priority = "Low",
        dueDate = due, referenceDoctype = "", referenceDocname = "", contactName = "",
        phone = "", normalizedPhone = "", isCompleted = done
    )

    @Test fun groupsByDueDateInDisplayOrder() {
        val groups = bucketTasks(
            listOf(
                task("future", "2026-11-20"),
                task("late", "2026-11-10 09:30:00"),
                task("now", "2026-11-16"),
                task("none", ""),
                task("finished", "2026-11-01", done = true)
            ),
            todayIso = "2026-11-16"
        )
        assertEquals(
            listOf(TaskBucket.OVERDUE, TaskBucket.TODAY, TaskBucket.UPCOMING, TaskBucket.ANYTIME, TaskBucket.DONE),
            groups.keys.toList()
        )
        assertEquals(listOf("late"), groups[TaskBucket.OVERDUE]!!.map { it.name })
    }

    @Test fun omitsEmptyBuckets() {
        val groups = bucketTasks(listOf(task("a", "2026-11-16")), "2026-11-16")
        assertEquals(listOf(TaskBucket.TODAY), groups.keys.toList())
    }

    @Test fun completedOverdueGoesToDone() {
        val groups = bucketTasks(listOf(task("a", "2020-01-01", done = true)), "2026-11-16")
        assertEquals(listOf(TaskBucket.DONE), groups.keys.toList())
    }
}
