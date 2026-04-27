package com.travelplanner.infrastructure.persistence.repository

import com.travelplanner.domain.model.Attachment
import com.travelplanner.domain.repository.AttachmentRepository
import com.travelplanner.infrastructure.persistence.DatabaseFactory.dbQuery
import com.travelplanner.infrastructure.persistence.tables.AttachmentsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID

class ExposedAttachmentRepository : AttachmentRepository {

    override suspend fun findByTrip(tripId: UUID): List<Attachment> = dbQuery {
        AttachmentsTable.selectAll()
            .where {
                (AttachmentsTable.tripId eq tripId) and AttachmentsTable.deletedAt.isNull()
            }
            .map { it.toAttachment() }
    }

    override suspend fun findByExpense(expenseId: UUID): List<Attachment> = dbQuery {
        AttachmentsTable.selectAll()
            .where {
                (AttachmentsTable.expenseId eq expenseId) and AttachmentsTable.deletedAt.isNull()
            }
            .map { it.toAttachment() }
    }

    override suspend fun findById(id: UUID): Attachment? = dbQuery {
        AttachmentsTable.selectAll()
            .where {
                (AttachmentsTable.id eq id) and AttachmentsTable.deletedAt.isNull()
            }
            .singleOrNull()
            ?.toAttachment()
    }

    override suspend fun create(attachment: Attachment): Attachment = dbQuery {
        AttachmentsTable.insert {
            it[id] = attachment.id
            it[tripId] = attachment.tripId
            it[expenseId] = attachment.expenseId
            it[uploadedBy] = attachment.uploadedBy
            it[fileName] = attachment.fileName
            it[fileSize] = attachment.fileSize
            it[mimeType] = attachment.mimeType
            it[s3Key] = attachment.s3Key
            it[createdAt] = attachment.createdAt
            it[deletedAt] = attachment.deletedAt
        }
        attachment
    }

    override suspend fun softDelete(id: UUID, deletedAt: Instant): Boolean = dbQuery {
        val updatedCount = AttachmentsTable.update({ AttachmentsTable.id eq id }) {
            it[AttachmentsTable.deletedAt] = deletedAt
        }
        updatedCount > 0
    }

    override suspend fun findModifiedAfter(tripId: UUID, after: Instant): List<Attachment> = dbQuery {
        AttachmentsTable.selectAll()
            .where {
                (AttachmentsTable.tripId eq tripId) and
                        ((AttachmentsTable.createdAt greater after) or
                                (AttachmentsTable.deletedAt greater after))
            }
            .map { it.toAttachment() }
    }

    // --- Mapping helper ---

    private fun ResultRow.toAttachment() = Attachment(
        id = this[AttachmentsTable.id],
        tripId = this[AttachmentsTable.tripId],
        expenseId = this[AttachmentsTable.expenseId],
        uploadedBy = this[AttachmentsTable.uploadedBy],
        fileName = this[AttachmentsTable.fileName],
        fileSize = this[AttachmentsTable.fileSize],
        mimeType = this[AttachmentsTable.mimeType],
        s3Key = this[AttachmentsTable.s3Key],
        createdAt = this[AttachmentsTable.createdAt],
        deletedAt = this[AttachmentsTable.deletedAt]
    )
}
