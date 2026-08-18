package com.echoflow

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.echoflow.data.extract.Anydoc
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnydocInstrumentedTest {
    @Test
    fun convertsDocxAndDigitalPdfToMarkdown() {
        assumeTrue("libanydoc.so is not available for this ABI", Anydoc.available)
        val assets = InstrumentationRegistry.getInstrumentation().context.assets

        val docx = assets.open("anydoc/text.docx").use { it.readBytes() }
        val docxResult = Anydoc.convert(docx, "text.docx")
        assertTrue("docx: $docxResult", docxResult is Anydoc.Result.Text && docxResult.markdown.isNotBlank())

        val pdf = assets.open("anydoc/text.pdf").use { it.readBytes() }
        val pdfResult = Anydoc.convert(pdf, "text.pdf")
        assertTrue("pdf: $pdfResult", pdfResult is Anydoc.Result.Text && pdfResult.markdown.isNotBlank())
    }
}
