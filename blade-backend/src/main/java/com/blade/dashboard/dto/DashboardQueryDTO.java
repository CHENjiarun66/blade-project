package com.blade.dashboard.dto;

import com.blade.dashboard.enums.PeriodType;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

/**
 * 仪表盘查询参数
 */
@Data
public class DashboardQueryDTO {

    @Parameter(description = "周期类型: TODAY, WEEK, MONTH, QUARTER, YEAR, CUSTOM")
    private PeriodType periodType = PeriodType.WEEK;

    @Parameter(description = "自定义开始日期 (yyyy-MM-dd)")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @Parameter(description = "自定义结束日期 (yyyy-MM-dd)")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /**
     * 获取当前周期的开始时间
     */
    public LocalDateTime getStartDateTime() {
        LocalDate today = LocalDate.now();
        LocalDate start = getStartDate(today);
        return start.atStartOfDay();
    }

    /**
     * 获取当前周期的结束时间
     */
    public LocalDateTime getEndDateTime() {
        LocalDate today = LocalDate.now();
        return today.atTime(LocalTime.MAX);
    }

    /**
     * 获取上一个同等周期的开始时间
     */
    public LocalDateTime getPreviousStartDateTime() {
        LocalDate today = LocalDate.now();
        LocalDate currentStart = getStartDate(today);
        return getPreviousStart(currentStart).atStartOfDay();
    }

    /**
     * 获取上一个同等周期的结束时间
     */
    public LocalDateTime getPreviousEndDateTime() {
        LocalDate today = LocalDate.now();
        LocalDate currentStart = getStartDate(today);
        LocalDate currentEnd = getEndDate(today);
        LocalDate previousEnd = getPreviousEnd(currentStart, currentEnd);
        return previousEnd.atTime(LocalTime.MAX);
    }

    private LocalDate getStartDate(LocalDate reference) {
        if (periodType == null || periodType == PeriodType.WEEK) {
            return reference.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        } else if (periodType == PeriodType.TODAY) {
            return reference;
        } else if (periodType == PeriodType.MONTH) {
            return reference.with(TemporalAdjusters.firstDayOfMonth());
        } else if (periodType == PeriodType.QUARTER) {
            int month = reference.getMonthValue();
            int quarterStartMonth = ((month - 1) / 3) * 3 + 1;
            return reference.withMonth(quarterStartMonth).with(TemporalAdjusters.firstDayOfMonth());
        } else if (periodType == PeriodType.YEAR) {
            return reference.with(TemporalAdjusters.firstDayOfYear());
        } else if (periodType == PeriodType.CUSTOM) {
            return startDate != null ? startDate : reference;
        }
        return reference.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    }

    private LocalDate getEndDate(LocalDate reference) {
        if (periodType == null || periodType == PeriodType.WEEK) {
            return reference;
        } else if (periodType == PeriodType.TODAY) {
            return reference;
        } else if (periodType == PeriodType.MONTH) {
            return reference;
        } else if (periodType == PeriodType.QUARTER) {
            return reference;
        } else if (periodType == PeriodType.YEAR) {
            return reference;
        } else if (periodType == PeriodType.CUSTOM) {
            return endDate != null ? endDate : reference;
        }
        return reference;
    }

    private LocalDate getPreviousStart(LocalDate currentStart) {
        if (periodType == null || periodType == PeriodType.WEEK) {
            return currentStart.minusWeeks(1);
        } else if (periodType == PeriodType.TODAY) {
            return currentStart.minusDays(1);
        } else if (periodType == PeriodType.MONTH) {
            return currentStart.minusMonths(1);
        } else if (periodType == PeriodType.QUARTER) {
            return currentStart.minusMonths(3);
        } else if (periodType == PeriodType.YEAR) {
            return currentStart.minusYears(1);
        } else if (periodType == PeriodType.CUSTOM && startDate != null && endDate != null) {
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
            return startDate.minusDays(daysBetween);
        }
        return currentStart.minusWeeks(1);
    }

    private LocalDate getPreviousEnd(LocalDate currentStart, LocalDate currentEnd) {
        if (periodType == null || periodType == PeriodType.WEEK) {
            return currentEnd.minusWeeks(1);
        } else if (periodType == PeriodType.TODAY) {
            return currentEnd.minusDays(1);
        } else if (periodType == PeriodType.MONTH) {
            return currentEnd.minusMonths(1);
        } else if (periodType == PeriodType.QUARTER) {
            return currentEnd.minusMonths(3);
        } else if (periodType == PeriodType.YEAR) {
            return currentEnd.minusYears(1);
        } else if (periodType == PeriodType.CUSTOM && startDate != null && endDate != null) {
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
            return endDate.minusDays(daysBetween);
        }
        return currentEnd.minusWeeks(1);
    }
}
