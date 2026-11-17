package com.qiongge.callassistant.data.repository

import com.qiongge.callassistant.data.local.entity.CustomerEntity
import com.qiongge.callassistant.data.local.entity.ImportDraftRowEntity
import com.qiongge.callassistant.data.local.entity.ImportSkipReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportDraftRulesTest {
    @Test
    fun `valid first occurrence wins and invalid or incomplete rows are skipped`() {
        val rows = listOf(
            row(1, "张三", " +86 138-0013-8000 "),
            row(2, "李四", "１３８００１３８０００"),
            row(3, "王五", "12345678901"),
            row(4, "赵六", "1380013800"),
            row(5, "", "13900139000"),
            row(6, "钱七", ""),
            row(7, "孙八", "13900139000"),
        )

        val result = ImportDraftRules.evaluate(rows)

        assertEquals(listOf(1L, 7L), result.filterNot { it.skipped }.map { it.id })
        assertEquals(ImportSkipReason.DUPLICATE_PHONE, result[1].skipReason)
        assertEquals(ImportSkipReason.INVALID_PHONE, result[2].skipReason)
        assertEquals(ImportSkipReason.INVALID_PHONE, result[3].skipReason)
        assertEquals(ImportSkipReason.INCOMPLETE, result[4].skipReason)
        assertEquals(ImportSkipReason.INCOMPLETE, result[5].skipReason)
    }

    @Test
    fun `duplicate is reconsidered when earlier row is manually skipped or removed`() {
        val first = row(1, "张三", "13800138000")
        val second = row(2, "李四", "13800138000")
        val initiallySkipped = ImportDraftRules.evaluate(listOf(first, second))[1]
        val manualSkip = first.copy(skipped = true, skipReason = ImportSkipReason.MANUAL)

        val afterSkip = ImportDraftRules.evaluate(listOf(manualSkip, initiallySkipped))
        assertTrue(afterSkip[0].skipped)
        assertFalse(afterSkip[1].skipped)
        assertNull(afterSkip[1].skipReason)
        assertFalse(ImportDraftRules.evaluate(listOf(initiallySkipped)).single().skipped)
    }

    @Test
    fun `restored independent duplicates stay included`() {
        val first = row(1, "张三", "13800138000")
        val restored = row(2, "李四", "13800138000")
            .copy(duplicateMode = LocalOcrImportRepository.CREATE_NEW)
        val third = row(3, "王五", "13800138000")

        val result = ImportDraftRules.evaluate(listOf(first, restored, third))

        assertEquals(listOf(1L, 2L), result.filterNot { it.skipped }.map { it.id })
        assertEquals(ImportSkipReason.DUPLICATE_PHONE, result[2].skipReason)
    }

    @Test
    fun `invalid row remains editable while typing but is skipped on final evaluation`() {
        val editing = row(1, "张三", "13800")
        assertFalse(ImportDraftRules.evaluate(listOf(editing), editingRowId = 1).single().skipped)
        assertEquals(ImportSkipReason.INVALID_PHONE, ImportDraftRules.evaluate(listOf(editing)).single().skipReason)
        val fixed = editing.copy(phone = "13800138000")
        assertFalse(ImportDraftRules.evaluate(listOf(fixed)).single().skipped)
    }

    @Test
    fun `correcting a skipped row restores it unless its number is already present`() {
        val invalid = row(2, "李四", "123").copy(skipped = true, skipReason = ImportSkipReason.INVALID_PHONE)
        val fixed = invalid.copy(phone = "13900139000")
        assertFalse(ImportDraftRules.evaluate(listOf(fixed)).single().skipped)

        val first = row(1, "张三", "13900139000")
        val duplicate = ImportDraftRules.evaluate(listOf(first, fixed))[1]
        assertTrue(duplicate.skipped)
        assertEquals(ImportSkipReason.DUPLICATE_PHONE, duplicate.skipReason)
    }

    @Test
    fun `existing match prefers explicit choice then same name and oldest id`() {
        val row = row(1, "李四", "13800138000")
        val matches = listOf(customer(9, "李四"), customer(2, "张三"), customer(4, "李四"))

        assertEquals(4L, ImportDraftRules.selectExistingCustomer(row, matches)?.id)
        assertEquals(2L, ImportDraftRules.selectExistingCustomer(row.copy(name = "其他"), matches)?.id)
        assertEquals(
            9L,
            ImportDraftRules.selectExistingCustomer(
                row.copy(duplicateMode = LocalOcrImportRepository.REUSE_EXISTING, duplicateCustomerId = 9),
                matches,
            )?.id,
        )
        assertEquals(
            4L,
            ImportDraftRules.selectExistingCustomer(
                row.copy(duplicateMode = LocalOcrImportRepository.REUSE_EXISTING, duplicateCustomerId = 99),
                matches,
            )?.id,
        )
        assertNull(ImportDraftRules.selectExistingCustomer(row, emptyList()))
    }

    private fun row(id: Long, name: String, phone: String) = ImportDraftRowEntity(
        id = id,
        sourceImageId = null,
        sortOrder = id.toInt(),
        name = name,
        phone = phone,
        sourceText = "",
        sourceLeft = null,
        sourceTop = null,
        sourceRight = null,
        sourceBottom = null,
        confidence = null,
        reviewed = false,
        skipped = false,
        duplicateMode = null,
        duplicateCustomerId = null,
    )

    private fun customer(id: Long, name: String) = CustomerEntity(id = id, name = name, phone = "13800138000")
}
