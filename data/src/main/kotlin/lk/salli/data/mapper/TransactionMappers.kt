package lk.salli.data.mapper

import lk.salli.data.db.entities.TransactionEntity
import lk.salli.domain.Money
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionMethod
import lk.salli.domain.TransactionType
import lk.salli.parser.ParsedTransaction

/**
 * Bridges between the parser's [ParsedTransaction] (pure domain) and Room's [TransactionEntity]
 * (persistence). Room is a data-layer concern that the parser stays unaware of.
 */

fun ParsedTransaction.toEntity(
    accountId: Long,
    method: TransactionMethod,
    nowMillis: Long,
): TransactionEntity = TransactionEntity(
    accountId = accountId,
    amountMinor = amount.minorUnits,
    amountCurrency = amount.currency,
    feeMinor = fee?.minorUnits,
    balanceMinor = balance?.minorUnits,
    timestamp = timestamp,
    flowId = flow.id,
    methodId = method.id,
    typeId = type.id,
    senderAddress = senderAddress,
    accountSuffix = accountNumberSuffix,
    merchantRaw = merchantRaw,
    location = location,
    rawBody = rawBody,
    isDeclined = isDeclined,
    createdAt = nowMillis,
    updatedAt = nowMillis,
)

fun TransactionEntity.toParsed(): ParsedTransaction = ParsedTransaction(
    senderAddress = senderAddress.orEmpty(),
    accountNumberSuffix = accountSuffix,
    amount = Money(amountMinor, amountCurrency),
    balance = balanceMinor?.let { Money(it, amountCurrency) },
    fee = feeMinor?.let { Money(it, amountCurrency) },
    flow = TransactionFlow.fromId(flowId),
    type = TransactionType.fromId(typeId),
    merchantRaw = merchantRaw,
    location = location,
    timestamp = timestamp,
    isDeclined = isDeclined,
    rawBody = rawBody.orEmpty(),
)
