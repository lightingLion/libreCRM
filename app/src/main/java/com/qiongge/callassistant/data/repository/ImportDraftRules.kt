package com.qiongge.callassistant.data.repository

import com.qiongge.callassistant.data.local.entity.CustomerEntity
import com.qiongge.callassistant.data.local.entity.ImportDraftRowEntity
import com.qiongge.callassistant.data.local.entity.ImportSkipReason
import com.qiongge.callassistant.domain.importing.PhoneRecognition

/** Applies to the whole draft in display order, so the first valid occurrence wins. */
internal object ImportDraftRules {
    fun evaluate(
        rows: List<ImportDraftRowEntity>,
        editingRowId: Long? = null,
    ): List<ImportDraftRowEntity> {
        val seenPhones = mutableSetOf<String>()
        return rows.map { row ->
            val complete = row.name.isNotBlank() && row.phone.isNotBlank()
            val previousReason = row.skipReason ?: when {
                row.skipped && complete -> ImportSkipReason.MANUAL
                row.skipped -> ImportSkipReason.INCOMPLETE
                else -> null
            }
            val phone = PhoneRecognition.comparableDigits(row.phone)
            when {
                previousReason == ImportSkipReason.MANUAL -> row.copy(skipped = true, skipReason = previousReason)

                // A restored or manually added row must remain editable while a user types.
                row.id == editingRowId && !row.skipped && (!complete || phone == null) ->
                    row.copy(reviewed = false, skipReason = null)

                !complete -> row.copy(
                    skipped = true,
                    reviewed = false,
                    duplicateMode = null,
                    duplicateCustomerId = null,
                    skipReason = ImportSkipReason.INCOMPLETE,
                )

                phone == null -> row.copy(
                    skipped = true,
                    reviewed = false,
                    duplicateMode = null,
                    duplicateCustomerId = null,
                    skipReason = ImportSkipReason.INVALID_PHONE,
                )

                row.duplicateMode == LocalOcrImportRepository.CREATE_NEW && !row.skipped -> {
                    seenPhones += phone
                    row.copy(skipped = false, skipReason = null)
                }

                seenPhones.add(phone) -> row.copy(skipped = false, skipReason = null)

                else -> row.copy(
                    skipped = true,
                    reviewed = false,
                    duplicateMode = null,
                    duplicateCustomerId = null,
                    skipReason = ImportSkipReason.DUPLICATE_PHONE,
                )
            }
        }
    }

    /** Prefer the same name when several existing customers share a number, then the oldest ID. */
    fun selectExistingCustomer(row: ImportDraftRowEntity, matches: List<CustomerEntity>): CustomerEntity? {
        val explicitlySelected = if (row.duplicateMode == LocalOcrImportRepository.REUSE_EXISTING) {
            matches.firstOrNull { it.id == row.duplicateCustomerId }
        } else {
            null
        }
        return explicitlySelected ?: matches.minWithOrNull(
            compareBy<CustomerEntity> { it.name.trim() != row.name.trim() }.thenBy { it.id },
        )
    }
}
