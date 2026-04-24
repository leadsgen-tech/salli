package lk.salli.parser.templates

import lk.salli.parser.fixtures.BocFixtures
import lk.salli.parser.fixtures.BocOnlineFixtures
import lk.salli.parser.fixtures.FixtureRunner
import lk.salli.parser.fixtures.ParseCase
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class BocTemplateTest {

    companion object {
        @JvmStatic
        fun bocCases(): Stream<Arguments> =
            (BocFixtures.cases + BocOnlineFixtures.cases)
                .map { Arguments.of(it.label, it) }
                .stream()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bocCases")
    fun parses(label: String, case: ParseCase) {
        FixtureRunner.run(case)
    }
}
