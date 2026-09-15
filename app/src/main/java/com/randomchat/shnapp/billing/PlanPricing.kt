package com.randomchat.shnapp.billing

import java.text.NumberFormat
import java.util.Currency
import kotlin.math.floor

/**
 * Paywall price maths. Everything derives from the real Play prices (micros +
 * ISO-8601 billing period), so per-week figures and savings stay true in every
 * currency instead of relying on hardcoded claims.
 */
object PlanPricing {

    private val PERIOD_REGEX = Regex("""^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?$""")

    /** Length of an ISO-8601 billing period ("P1W", "P1M", "P1Y") in weeks, or null. */
    fun weeksIn(isoPeriod: String): Double? {
        val match = PERIOD_REGEX.matchEntire(isoPeriod) ?: return null
        val (years, months, weeks, days) = match.destructured
        val total = (years.toIntOrNull() ?: 0) * 52.1786 +
            (months.toIntOrNull() ?: 0) * 4.34524 +
            (weeks.toIntOrNull() ?: 0) +
            (days.toIntOrNull() ?: 0) / 7.0
        return total.takeIf { it > 0.0 }
    }

    /** Price per week in micros, or null when the billing period can't be parsed. */
    fun perWeekMicros(plan: PremiumPlan): Double? =
        weeksIn(plan.billingPeriod)?.let { plan.priceMicros / it }

    /**
     * Whole-percent per-week saving of [plan] against [baseline], rounded down so it
     * is never overstated. null when the saving is under 5 % or not comparable.
     */
    fun savingsPercent(plan: PremiumPlan, baseline: PremiumPlan): Int? {
        if (plan.currencyCode != baseline.currencyCode) return null
        val planWeek = perWeekMicros(plan) ?: return null
        val baseWeek = perWeekMicros(baseline) ?: return null
        if (baseWeek <= 0.0) return null
        val percent = floor((1.0 - planWeek / baseWeek) * 100.0).toInt()
        return percent.takeIf { it >= 5 }
    }

    /** Formats a micros amount in [currencyCode], e.g. "₹34.29". */
    fun formatMicros(micros: Double, currencyCode: String): String {
        val format = NumberFormat.getCurrencyInstance()
        runCatching { Currency.getInstance(currencyCode) }.getOrNull()?.let { currency ->
            format.currency = currency
            val digits = currency.defaultFractionDigits.coerceAtLeast(0)
            format.minimumFractionDigits = digits
            format.maximumFractionDigits = digits
        }
        return format.format(micros / 1_000_000.0)
    }

    /** "week" / "month" / "3 months" / "year" for an ISO-8601 period, or null. */
    fun unitLabel(isoPeriod: String): String? {
        val match = PERIOD_REGEX.matchEntire(isoPeriod) ?: return null
        val (years, months, weeks, days) = match.destructured
        return label(years, "year") ?: label(months, "month") ?: label(weeks, "week") ?: label(days, "day")
    }

    private fun label(count: String, unit: String): String? {
        val n = count.toIntOrNull() ?: return null
        return if (n == 1) unit else "$n ${unit}s"
    }
}
