package lk.salli.parser.templates

import lk.salli.parser.fixtures.CombankFixtures
import lk.salli.parser.fixtures.FixtureRunner
import lk.salli.parser.fixtures.ParseCase
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class CombankTemplateTest {

    companion object {
        @JvmStatic
        fun cases(): Stream<Arguments> =
            CombankFixtures.cases.map { Arguments.of(it.label, it) }.stream()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun parses(label: String, case: ParseCase) {
        FixtureRunner.run(case)
    }
}
