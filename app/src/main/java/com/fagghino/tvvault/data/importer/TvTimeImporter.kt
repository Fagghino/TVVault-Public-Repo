package com.fagghino.tvvault.data.importer

import android.content.Context
import android.net.Uri
import com.fagghino.tvvault.data.local.entity.*
import com.fagghino.tvvault.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class TvTimeImporter(
    private val context: Context,
    private val repository: MediaRepository
) {
    suspend fun importFollowedShows(fileUri: Uri): Long = withContext(Dispatchers.IO) {
        val jobDao = repository.getImportJobDao()
        val candidateDao = repository.getImportMatchCandidateDao()

        // 1. Create a running ImportJob
        val jobId = jobDao.insert(
            ImportJob(
                type = "csv",
                fileName = "followed_tv_show.csv",
                status = "running"
            )
        )
        
        var totalRows = 0
        var matchedRows = 0
        var ambiguousRows = 0
        var unmatchedRows = 0

        try {
            val contentResolver = context.contentResolver
            contentResolver.openInputStream(fileUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    // Skip header row
                    val headerLine = reader.readLine()
                    
                    var line = reader.readLine()
                    while (line != null) {
                        if (line.isBlank()) {
                            line = reader.readLine()
                            continue
                        }
                        totalRows++
                        val tokens = parseCsvLine(line)
                        if (tokens.size >= 2) {
                            val tvShowName = tokens[1]
                            
                            // Query TMDb online by show name
                            val results = repository.searchTvShowsOnline(tvShowName)
                            
                            if (results.isEmpty()) {
                                unmatchedRows++
                            } else if (results.size == 1) {
                                // Direct single match! Import into library
                                repository.addMediaToLibrary(results[0])
                                matchedRows++
                            } else {
                                // Ambiguous results: save candidates for reconciliation
                                ambiguousRows++
                                results.take(3).forEach { candidate ->
                                    candidateDao.insert(
                                        ImportMatchCandidate(
                                            importJobId = jobId,
                                            rawTitle = tvShowName,
                                            rawYear = candidate.releaseDate?.take(4),
                                            rawType = "tv",
                                            candidateProviderId = candidate.providerId,
                                            candidateTitle = candidate.title,
                                            score = computeMatchScore(tvShowName, candidate.title)
                                        )
                                    )
                                }
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }
            
            // Job completed, set reconciliation flag if ambiguous items remain
            val status = if (ambiguousRows > 0) "requires_reconciliation" else "completed"
            val job = jobDao.getById(jobId)?.copy(
                status = status,
                finishedAt = System.currentTimeMillis(),
                totalRows = totalRows,
                matchedRows = matchedRows,
                ambiguousRows = ambiguousRows,
                unmatchedRows = unmatchedRows
            )
            job?.let { jobDao.insert(it) }
        } catch (e: Exception) {
            val job = jobDao.getById(jobId)?.copy(
                status = "failed",
                finishedAt = System.currentTimeMillis(),
                notes = e.localizedMessage
            )
            job?.let { jobDao.insert(it) }
        }
        jobId
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val currentToken = StringBuilder()
        
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                result.add(currentToken.toString().trim())
                currentToken.setLength(0)
            } else {
                currentToken.append(c)
            }
            i++
        }
        result.add(currentToken.toString().trim())
        return result
    }

    private fun computeMatchScore(query: String, candidate: String): Float {
        return if (query.equals(candidate, ignoreCase = true)) 1.0f else 0.5f
    }
}
