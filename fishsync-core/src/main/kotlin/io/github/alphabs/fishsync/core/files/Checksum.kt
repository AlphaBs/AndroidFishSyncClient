package io.github.alphabs.fishsync.core.files

data class Checksum(
    val algorithm: String,
    val hex: String,
) {
    init {
        val length =
            when (algorithm) {
                "md5" -> 32
                "sha1" -> 40
                else -> error("Unsupported checksum algorithm")
            }
        require(
            hex.length == length &&
                hex.all {
                    it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F'
                },
        ) { "Invalid checksum" }
    }
}
