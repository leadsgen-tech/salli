package lk.salli.app.nav

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The information architecture, pinned.
 *
 * Route strings survive in saved back-stack state on installed builds, so changing one is a
 * migration, not a rename. These tests make that explicit: they fail loudly if a route is
 * renamed, if a fifth tab appears, or if the Activity deep-link contract stops round-tripping.
 *
 * Robolectric only because `Destination` builds `ImageVector`s and reads `R.string` ids.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationIaTest {

    @Test
    fun `there are exactly four tabs, in reading order`() {
        assertThat(Destination.entries.map { it.route })
            .containsExactly("home", "timeline", "plan", "insights")
            .inOrder()
    }

    @Test
    fun `Activity keeps the timeline route despite the rename`() {
        // The tab is labelled "Activity" now; the route is still "timeline" because installed
        // builds have it in saved state and renaming it buys nothing.
        assertThat(Destination.ACTIVITY.route).isEqualTo("timeline")
    }

    @Test
    fun `settings and budgets are pushes, not tabs`() {
        val tabRoutes = Destination.entries.map { it.route }
        assertThat(tabRoutes).doesNotContain(Route.SETTINGS)
        assertThat(tabRoutes).doesNotContain(Route.BUDGETS)
    }

    @Test
    fun `every tab has a distinct route, pattern and label`() {
        assertThat(Destination.entries.map { it.route }.toSet()).hasSize(Destination.entries.size)
        assertThat(Destination.entries.map { it.pattern }.toSet()).hasSize(Destination.entries.size)
        assertThat(Destination.entries.map { it.labelRes }.toSet()).hasSize(Destination.entries.size)
    }

    @Test
    fun `selected icons differ from unselected ones`() {
        // Outlined to filled on select is the only cue besides the lime lozenge; if a tab
        // reuses one vector for both, the tab silently stops responding visually.
        Destination.entries.forEach { dest ->
            assertThat(dest.icon).isNotEqualTo(dest.selectedIcon)
        }
    }

    @Test
    fun `only Activity carries arguments in its pattern`() {
        Destination.entries.forEach { dest ->
            if (dest == Destination.ACTIVITY) {
                assertThat(dest.pattern).startsWith("${dest.route}?")
            } else {
                assertThat(dest.pattern).isEqualTo(dest.route)
            }
        }
    }

    @Test
    fun `the Activity pattern declares all three filters`() {
        assertThat(Destination.ACTIVITY.pattern)
            .isEqualTo("timeline?account={account}&category={category}&q={q}")
    }

    @Test
    fun `an unfiltered Activity route is the bare route`() {
        // Otherwise the nav bar would treat "timeline?account=null&..." as a different
        // destination from the tab it just highlighted.
        assertThat(Route.activity()).isEqualTo("timeline")
        assertThat(Route.activity(query = "   ")).isEqualTo("timeline")
    }

    @Test
    fun `filters appear only when set`() {
        assertThat(Route.activity(accountId = 42L)).isEqualTo("timeline?account=42")
        assertThat(Route.activity(categoryId = 7L)).isEqualTo("timeline?category=7")
        assertThat(Route.activity(accountId = 42L, categoryId = 7L))
            .isEqualTo("timeline?account=42&category=7")
    }

    @Test
    fun `a search term is percent-encoded with spaces as %20, not plus`() {
        // Navigation decodes with Uri.decode, which leaves "+" alone — a form-encoded space
        // would arrive as a literal plus and match nothing.
        assertThat(Route.activity(query = "keells super")).isEqualTo("timeline?q=keells%20super")
        assertThat(Route.activity(query = "a&b=c")).isEqualTo("timeline?q=a%26b%3Dc")
    }

    @Test
    fun `filter args parse back out of their string form`() {
        val args = ActivityFilterArgs.from(account = "42", category = "7", query = "keells")
        assertThat(args.accountId).isEqualTo(42L)
        assertThat(args.categoryId).isEqualTo(7L)
        assertThat(args.query).isEqualTo("keells")
        assertThat(args.isEmpty).isFalse()
    }

    @Test
    fun `absent filters parse to an empty set rather than to zero ids`() {
        // The reason ids travel as strings: a nullable NavType.LongType would hand us 0L,
        // which looks like a real row id and would filter the list down to nothing.
        val args = ActivityFilterArgs.from(account = null, category = null, query = null)
        assertThat(args).isEqualTo(ActivityFilterArgs.NONE)
        assertThat(args.isEmpty).isTrue()
    }

    @Test
    fun `junk in a deep link degrades to no filter instead of throwing`() {
        val args = ActivityFilterArgs.from(account = "banana", category = "", query = "  ")
        assertThat(args.accountId).isNull()
        assertThat(args.categoryId).isNull()
        assertThat(args.query).isNull()
        assertThat(args.isEmpty).isTrue()
    }
}
