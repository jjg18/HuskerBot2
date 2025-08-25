package org.j3y.HuskerBot2.automation.backup

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.BufferedWriter
import java.io.File
import java.nio.file.*
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.sql.Time
import java.sql.Timestamp
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.sql.DataSource

@Service
class DatabaseBackupService(
    private val dataSource: DataSource,
    @Value("\${backup.sql.dir:backups/sql}") backupDir: String
) {
    private val log = LoggerFactory.getLogger(DatabaseBackupService::class.java)

    private val backupRoot: Path = Paths.get(backupDir)
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val zone: ZoneId = ZoneId.systemDefault()

    fun runBackup() {
        val start = System.currentTimeMillis()
        try {
            Files.createDirectories(backupRoot)

            dataSource.connection.use { conn ->
                val timestamp = ZonedDateTime.now(zone).format(dateTimeFormatter)
                val tempDir = Files.createTempDirectory("sql-backup-$timestamp-")

                try {
                    val tables = listTables(conn)
                    log.info("Starting database backup for {} tables", tables.size)
                    for (table in tables) {
                        dumpTableToFile(conn, table, tempDir)
                    }

                    val zipName = "sql-backup-$timestamp.zip"
                    val zipPath = backupRoot.resolve(zipName)
                    zipDirectory(tempDir, zipPath)
                    log.info("Database backup created at {}", zipPath.toAbsolutePath())
                } finally {
                    // Clean temp dir
                    try {
                        Files.walk(tempDir)
                            .sorted(Comparator.reverseOrder())
                            .forEach { Files.deleteIfExists(it) }
                    } catch (ex: Exception) {
                        log.warn("Failed to cleanup temp backup directory {}: {}", tempDir, ex.message)
                    }
                }
            }

            deleteOldBackups(Duration.ofDays(15))
        } catch (ex: Exception) {
            log.error("Database backup failed: {}", ex.message, ex)
        } finally {
            val elapsed = System.currentTimeMillis() - start
            log.info("Database backup finished in {} ms", elapsed)
        }
    }

    private fun listTables(conn: Connection): List<String> {
        val tables = mutableListOf<String>()
        val meta = conn.metaData
        meta.getTables(null, null, "%", arrayOf("TABLE")).use { rs ->
            while (rs.next()) {
                val tableName = rs.getString("TABLE_NAME")
                val schema = rs.getString("TABLE_SCHEM")
                // Skip system schemas/tables (heuristic for H2 and MySQL)
                val lowerSchema = schema?.lowercase()
                if (lowerSchema == null || (!lowerSchema.startsWith("information_schema") && !lowerSchema.startsWith("mysql") && !lowerSchema.startsWith("performance_schema") && !lowerSchema.startsWith("sys"))) {
                    tables.add(tableName)
                }
            }
        }
        tables.sort()
        return tables
    }

    private fun dumpTableToFile(conn: Connection, table: String, dir: Path) {
        val file = dir.resolve("$table.sql").toFile()
        file.bufferedWriter().use { writer ->
            writer.write("-- Backup of table $table\n")
            writer.write("-- Generated at ${ZonedDateTime.now(zone)}\n\n")
            val sql = "SELECT * FROM $table"
            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    writeInserts(writer, table, rs)
                }
            }
        }
    }

    private fun writeInserts(writer: BufferedWriter, table: String, rs: ResultSet) {
        val meta = rs.metaData
        val columnCount = meta.columnCount
        val columns = (1..columnCount).map { meta.getColumnLabel(it) }
        val columnList = columns.joinToString(", ")
        var rowCount = 0
        while (rs.next()) {
            val values = (1..columnCount).joinToString(", ") { idx ->
                formatValue(rs.getObject(idx))
            }
            val insert = "INSERT INTO $table ($columnList) VALUES ($values);"
            writer.write(insert)
            writer.newLine()
            rowCount++
        }
        if (rowCount == 0) {
            // Still write an empty marker for clarity
            writer.write("-- (no rows)\n")
        }
    }

    private fun formatValue(value: Any?): String {
        if (value == null) return "NULL"
        return when (value) {
            is String -> "'" + escapeSql(value) + "'"
            is Boolean -> if (value) "TRUE" else "FALSE"
            is Int, is Long, is Short, is Float, is Double, is java.math.BigDecimal -> value.toString()
            is Date -> "'" + value.toLocalDate().toString() + "'"
            is Time -> "'" + value.toLocalTime().toString() + "'"
            is Timestamp -> "'" + value.toInstant().toString().replace("T", " ").removeSuffix("Z") + "'"
            else -> "'" + escapeSql(value.toString()) + "'"
        }
    }

    private fun escapeSql(input: String): String {
        return input.replace("'", "''")
    }

    private fun zipDirectory(sourceDir: Path, zipPath: Path) {
        Files.createDirectories(zipPath.parent)
        ZipOutputStream(Files.newOutputStream(zipPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)).use { zos ->
            Files.walk(sourceDir).use { stream ->
                stream.filter { Files.isRegularFile(it) }.forEach { file ->
                    val entryName = sourceDir.relativize(file).toString().replace('\\', '/')
                    val entry = ZipEntry(entryName)
                    zos.putNextEntry(entry)
                    Files.copy(file, zos)
                    zos.closeEntry()
                }
            }
        }
    }

    private fun deleteOldBackups(retention: Duration) {
        try {
            if (!Files.exists(backupRoot)) return
            val cutoff = System.currentTimeMillis() - retention.toMillis()
            Files.list(backupRoot).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.fileName.toString().startsWith("sql-backup-") && it.fileName.toString().endsWith(".zip") }
                    .forEach { path ->
                        try {
                            val lastMod = Files.getLastModifiedTime(path).toMillis()
                            if (lastMod < cutoff) {
                                Files.deleteIfExists(path)
                                log.info("Deleted old backup {}", path.fileName)
                            }
                        } catch (ex: Exception) {
                            log.warn("Failed to check/delete old backup {}: {}", path.fileName, ex.message)
                        }
                    }
            }
        } catch (ex: Exception) {
            log.warn("Error during old backup cleanup: {}", ex.message)
        }
    }
}
