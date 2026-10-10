package com.cloudcost.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class CsvWriterTest {

    @Test
    void quotesFieldsWithSeparatorsQuotesAndLineBreaks() {
        String csv = new CsvWriter()
                .text("plain").text("a,b").text("say \"hi\"").text("two\nlines").endRow()
                .toString();

        assertThat(csv).isEqualTo("plain,\"a,b\",\"say \"\"hi\"\"\",\"two\nlines\"\r\n");
    }

    @Test
    void neutralizesTextThatSpreadsheetsWouldTreatAsFormulas() {
        String csv = new CsvWriter()
                .text("=HYPERLINK(\"http://evil\")").text("+1").text("-2").text("@SUM(A1)").text("\tx").endRow()
                .toString();

        assertThat(csv).isEqualTo("\"'=HYPERLINK(\"\"http://evil\"\")\",'+1,'-2,'@SUM(A1),'\tx\r\n");
    }

    @Test
    void writesAmountsAsPlainNumbersSoNegativeValuesStayNumeric() {
        String csv = new CsvWriter()
                .amount(new BigDecimal("-12.345")).amount(new BigDecimal("1E+3")).amount(null)
                .number(new BigDecimal("12.50")).endRow()
                .toString();

        assertThat(csv).isEqualTo("-12.35,1000.00,,12.50\r\n");
    }
}
