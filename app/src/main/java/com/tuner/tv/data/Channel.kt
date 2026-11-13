package com.tuner.tv.data

data class Channel(
    val id: Int,
    val number: Int,
    val name: String,
    val url: String,
    val logo: String?,
    val group: String,
    val tvgId: String?,
) {
    /** Keys this channel can be matched to the TV guide by: its tvg-id, then its name. */
    val guideKeys: List<String> by lazy {
        listOfNotNull(tvgId?.lowercase(), "n:" + normaliseName(name))
    }
}

/** "TVNZ 1 HD" -> "tvnz1", so names match guide entries despite small differences. */
fun normaliseName(s: String): String {
    var n = s.lowercase().replace(Regex("[^a-z0-9]"), "")
    for (suffix in listOf("fhd", "uhd", "hd", "sd", "4k")) {
        if (n.length > suffix.length + 1 && n.endsWith(suffix)) {
            n = n.removeSuffix(suffix)
            break
        }
    }
    return n
}
