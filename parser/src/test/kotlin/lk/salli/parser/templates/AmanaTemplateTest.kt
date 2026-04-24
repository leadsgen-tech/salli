package lk.salli.parser.templates

import lk.salli.parser.fixtures.AmanaFixtures
import lk.salli.parser.fixtures.FixtureRunner
import lk.salli.parser.fixtures.ParseCase
import java.util.stream.Stream
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class AmanaTemplateTest {

    companion object {
        @JvmStatic
        fun cases(): Stream<Arguments> =
            AmanaFixtures.cases.map { Arguments.of(it.label, it) }.stream()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun parses(label: String, case: ParseCase) {
        FixtureRunner.run(case)
    }
}
