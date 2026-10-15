package io.github.alphabs.fishsync.core.internals

import java.io.File

/** Java File이 제거하는 끝 구분자를 원본 파일 API에 전달하기 전에 확인한다. */
internal fun endsInNativeDirectorySeparator(path: String): Boolean =
    path.endsWith(File.separatorChar) || (File.separatorChar == '\\' && path.endsWith('/'))
