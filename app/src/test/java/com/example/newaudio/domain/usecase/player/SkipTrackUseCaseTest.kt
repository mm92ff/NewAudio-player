package com.example.newaudio.domain.usecase.player

import com.example.newaudio.fake.FakeMediaRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SkipTrackUseCaseTest {

    private val repo = FakeMediaRepository()
    private val useCase = SkipTrackUseCase(repo)

    @Test
    fun `next delegates to repository skipNext`() = runTest {
        useCase.next()
        assertEquals(1, repo.skipNextCalled)
    }

    @Test
    fun `previous delegates to repository skipPrevious`() = runTest {
        useCase.previous()
        assertEquals(1, repo.skipPreviousCalled)
    }

    @Test
    fun `next and previous are independent`() = runTest {
        useCase.next()
        useCase.next()
        useCase.previous()
        assertEquals(2, repo.skipNextCalled)
        assertEquals(1, repo.skipPreviousCalled)
    }
}
