package com.pfm.controller;

import com.pfm.dto.Dto.*;
import com.pfm.security.CurrentUser;
import com.pfm.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Monthly and yearly report endpoints. */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;
    private final CurrentUser currentUser;

    @GetMapping("/monthly/{year}/{month}")
    public MonthlyReport monthly(@PathVariable int year, @PathVariable int month) {
        return reportService.monthly(currentUser.id(), year, month);
    }

    @GetMapping("/yearly/{year}")
    public YearlyReport yearly(@PathVariable int year) {
        return reportService.yearly(currentUser.id(), year);
    }
}
