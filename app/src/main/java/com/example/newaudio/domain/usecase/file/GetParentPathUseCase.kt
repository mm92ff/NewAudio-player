package com.example.newaudio.domain.usecase.file

import java.io.File
import javax.inject.Inject

class GetParentPathUseCase @Inject constructor() {
    operator fun invoke(path: String): String? {
        return File(path).parent
    }
}
