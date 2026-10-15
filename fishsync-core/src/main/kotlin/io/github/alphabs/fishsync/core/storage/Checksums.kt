package io.github.alphabs.fishsync.core.storage

import io.github.alphabs.fishsync.core.ChecksumAlgorithms
import java.io.InputStream

suspend fun hash(
    input: InputStream,
    algorithm: String = "md5",
): String = ChecksumAlgorithms.computeHashAsync(algorithm, input)
