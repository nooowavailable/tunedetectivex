package com.dev.tunedetectivex

fun String.toSafeArtwork(): String {
    return if (this.contains("mzstatic.com")) {
        this.replace("100x100bb", "600x600bb")
    } else this
}
