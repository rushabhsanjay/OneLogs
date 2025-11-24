data class DiaryEntry(
    val entryUniqueId: Long,
    val firstEntryDate: String,
    val firstTimeStamp: String,
    val linkedId: Long?,
    val entryType: String,
    val taskStat: String?,
    val filepath: String?,
    val textTask: String,
    val note: String?,
    val deleteStat: Boolean
)
