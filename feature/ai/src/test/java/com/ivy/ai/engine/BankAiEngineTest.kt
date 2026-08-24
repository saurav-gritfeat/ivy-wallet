package com.ivy.ai.engine

import com.ivy.ai.data.BankTemplateRepository
import com.ivy.ai.model.BankFewShotTemplate
import com.ivy.base.model.TransactionType
import com.ivy.data.model.CategoryId
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.CategoryRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.util.UUID

class BankAiEngineTest {

    private val templateRepository: BankTemplateRepository = mockk()
    private val categoryRepository: CategoryRepository = mockk()
    private val mediaPipeLlmEngine: MediaPipeLlmEngine = mockk(relaxed = true)
    private lateinit var engine: BankAiEngine

    @Before
    fun setup() {
        val dummyCategory = com.ivy.data.model.Category(
            id = CategoryId(UUID.randomUUID()),
            name = NotBlankTrimmedString.unsafe("Food & Drinks"),
            color = ColorInt.unsafe(0),
            icon = null,
            orderNum = 0.0
        )
        coEvery { categoryRepository.findAll() } returns listOf(dummyCategory)
        every { templateRepository.getTemplates() } returns flowOf(emptyList<BankFewShotTemplate>())
        every { mediaPipeLlmEngine.isModelLoaded() } returns false

        engine = BankAiEngine(templateRepository, categoryRepository, mediaPipeLlmEngine)
    }

    @Test
    fun `parses standard debit expense message accurately`() = runBlocking {
        val message = "Your A/C *1234 debited by NPR 450.00 on 2026-08-20 for payment to HIMALAYAN JAVA COFFEE via Fonepay."
        val result = engine.parse(message)

        result.amount shouldBe 450.0
        result.currency shouldBe "NPR"
        result.type shouldBe TransactionType.EXPENSE
        result.merchant shouldBe "Himalayan Java Coffee"
        result.categoryName shouldBe "Food & Drinks"
    }

    @Test
    fun `parses salary credit income message accurately`() = runBlocking {
        val message = "Your Account *9901 has been CREDITED by NPR 85,000.00 for SALARY AUGUST from ACME CORP."
        val result = engine.parse(message)

        result.amount shouldBe 85000.0
        result.currency shouldBe "NPR"
        result.type shouldBe TransactionType.INCOME
        result.merchant shouldBe "Acme Corp (Salary)"
    }

    @Test
    fun `parses US card alert accurately`() = runBlocking {
        val message = "Chase Alert: Your card ending in 4921 was charged $38.75 at UBER EATS on Aug 24."
        val result = engine.parse(message)

        result.amount shouldBe 38.75
        result.currency shouldBe "USD"
        result.type shouldBe TransactionType.EXPENSE
        result.merchant shouldBe "Uber Eats"
    }
}
