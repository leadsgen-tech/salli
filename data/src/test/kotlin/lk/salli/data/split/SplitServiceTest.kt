package lk.salli.data.split

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.db.entities.TransactionEntity
import lk.salli.domain.split.ExpenseInput
import lk.salli.domain.split.SettlementInput
import lk.salli.domain.split.SplitError
import lk.salli.domain.split.SplitMath
import lk.salli.domain.split.SplitMethod
import lk.salli.domain.split.SplitParticipant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SplitServiceTest {

    private lateinit var db: SalliDatabase
    private lateinit var service: SplitService

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SalliDatabase::class.java)
            .allowMainThreadQueries().build()
        service = SplitService(db, now = { 1_000L })
    }

    @After
    fun tearDown() = db.close()

    private fun saved(outcome: SplitService.Outcome) = (outcome as SplitService.Outcome.Saved).id

    @Test
    fun `a new group starts with the user as its me member`(): Unit = runBlocking {
        val groupId = service.createGroup("Ella trip", "lkr", meName = " ")
        val members = db.split().membersOf(groupId)
        assertThat(members).hasSize(1)
        assertThat(members.single().isMe).isTrue()
        assertThat(members.single().name).isEqualTo("Me")
        assertThat(db.split().groupById(groupId)!!.currency).isEqualTo("LKR")
    }

    @Test
    fun `an expense is stored with shares that reconcile and balances that sum to zero`(): Unit = runBlocking {
        val groupId = service.createGroup("Ella trip", "LKR", "Nabil")
        val me = db.split().membersOf(groupId).single().id
        val aadhil = saved(service.addMember(groupId, "Aadhil"))
        val kasun = saved(service.addMember(groupId, "Kasun"))

        val expenseId = saved(
            service.addExpense(
                groupId, "Hotel", 100_000, "LKR", paidByMemberId = me, method = SplitMethod.EQUAL,
                participants = listOf(SplitParticipant(me), SplitParticipant(aadhil), SplitParticipant(kasun)),
            ),
        )
        val shares = db.split().sharesOf(expenseId)
        assertThat(shares.map { it.shareMinor }).containsExactly(33_334L, 33_333L, 33_333L)

        saved(service.recordSettlement(groupId, fromMemberId = aadhil, toMemberId = me, amountMinor = 33_333))
        val net = SplitMath.balances(
            listOf(me, aadhil, kasun),
            listOf(ExpenseInput(me, 100_000, shares.associate { it.memberId to it.shareMinor })),
            listOf(SettlementInput(aadhil, me, 33_333)),
        )
        assertThat(net.values.sum()).isEqualTo(0L)
        assertThat(net[aadhil]).isEqualTo(0L)
        assertThat(net[kasun]).isEqualTo(-33_333L)
    }

    @Test
    fun `mismatched currency, bad exact amounts and strangers are refused`(): Unit = runBlocking {
        val groupId = service.createGroup("Flat", "LKR", "Nabil")
        val me = db.split().membersOf(groupId).single().id
        val friend = saved(service.addMember(groupId, "Friend"))
        val both = listOf(SplitParticipant(me, 600), SplitParticipant(friend, 300))

        assertThat(service.addExpense(groupId, "Netflix", 900, "USD", me, SplitMethod.EXACT, both))
            .isEqualTo(SplitService.Outcome.Rejected(SplitError.CURRENCY_MISMATCH))
        assertThat(service.addExpense(groupId, "Rent", 1_000, "LKR", me, SplitMethod.EXACT, both))
            .isEqualTo(SplitService.Outcome.Rejected(SplitError.EXACT_MISMATCH))
        assertThat(service.addExpense(groupId, "Rent", 900, "LKR", me, SplitMethod.EXACT, both + SplitParticipant(999, 0)))
            .isEqualTo(SplitService.Outcome.Rejected(SplitError.UNKNOWN_MEMBER))
        assertThat(service.recordSettlement(groupId, me, me, 100))
            .isEqualTo(SplitService.Outcome.Rejected(SplitError.SAME_MEMBER))
        assertThat(db.split().sharesOf(1)).isEmpty()
    }

    @Test
    fun `a split from a real transaction shows the user's share and never touches the transaction`(): Unit = runBlocking {
        db.accounts().insert(AccountEntity(id = 1, senderAddress = "COMBANK", accountSuffix = "#4273", displayName = "ComBank", currency = "LKR", accountTypeId = 0))
        val tx = TransactionEntity(id = 50, accountId = 1, amountMinor = 450_000, amountCurrency = "LKR", timestamp = 5L, flowId = 0, methodId = 1, typeId = 0, merchantRaw = "PIZZA HUT", createdAt = 1L, updatedAt = 1L)
        db.transactions().insert(tx)
        val groupId = service.createGroup("Dinner", "LKR", "Nabil")
        val me = db.split().membersOf(groupId).single().id
        val friend = saved(service.addMember(groupId, "Friend"))
        service.addExpense(
            groupId, "PIZZA HUT", 450_000, "LKR", me, SplitMethod.WEIGHTS,
            listOf(SplitParticipant(me, 1), SplitParticipant(friend, 2)), at = tx.timestamp, linkedTransactionId = 50,
        )
        val linked = service.linkedSplit(50)!!
        assertThat(linked.groupName).isEqualTo("Dinner")
        assertThat(linked.myShareMinor).isEqualTo(150_000L)
        assertThat(db.transactions().byId(50)).isEqualTo(tx)
        assertThat(service.linkedSplit(51)).isNull()
    }

    @Test
    fun `deleting a group removes its members, expenses, shares and settlements only`(): Unit = runBlocking {
        val keep = service.createGroup("Keep", "LKR", "Nabil")
        val keepMe = db.split().membersOf(keep).single().id
        service.addExpense(keep, "Tea", 500, "LKR", keepMe, SplitMethod.EQUAL, listOf(SplitParticipant(keepMe)))

        val drop = service.createGroup("Drop", "LKR", "Nabil")
        val me = db.split().membersOf(drop).single().id
        val friend = saved(service.addMember(drop, "Friend"))
        service.addExpense(drop, "Lunch", 2_000, "LKR", me, SplitMethod.EQUAL, listOf(SplitParticipant(me), SplitParticipant(friend)))
        service.recordSettlement(drop, friend, me, 1_000)

        service.deleteGroup(drop)

        val backup = db.backup()
        assertThat(backup.allSplitGroups().map { it.id }).containsExactly(keep)
        assertThat(backup.allSplitMembers().map { it.groupId }.toSet()).containsExactly(keep)
        assertThat(backup.allSplitExpenses().map { it.title }).containsExactly("Tea")
        assertThat(backup.allSplitShares()).hasSize(1)
        assertThat(backup.allSplitSettlements()).isEmpty()
    }
}
