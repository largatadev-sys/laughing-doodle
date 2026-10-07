package com.largatadev.timesheet.handoffs;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The handoffs module is split into role sub-packages like {@code dashboard/}. Sub-packages
 * force the repository and the Handoff write model public; these rules put the guarantee back
 * — nothing outside the module writes a Handoff or reads its tables directly — and pin the
 * layering. No other module uses Handoffs today (the reports read stopped embedding them on
 * 2026-10-07); one that needs them later goes through {@code handoffs.service} and
 * {@code handoffs.dto}.
 */
@AnalyzeClasses(packages = "com.largatadev.timesheet", importOptions = ImportOption.DoNotIncludeTests.class)
class HandoffsArchitectureTest {

	@ArchTest
	static final ArchRule repositoryAndDomainStayInsideTheModule = classes()
			.that().resideInAnyPackage("..handoffs.repository..", "..handoffs.domain..")
			.should().onlyBeAccessed().byAnyPackage("..handoffs..")
			.because("a Handoff is written only through the create route's checks and is never edited");

	@ArchTest
	static final ArchRule controllersDoNotReachTheRepository = noClasses()
			.that().resideInAPackage("..handoffs.controller..")
			.should().dependOnClassesThat().resideInAPackage("..handoffs.repository..")
			.because("controller → service → repository (CLAUDE.md conventions)");

	@ArchTest
	static final ArchRule onlyServicesUseTheRepository = classes()
			.that().resideInAPackage("..handoffs.repository..")
			.should().onlyBeAccessed().byAnyPackage("..handoffs.service..", "..handoffs.repository..");

	@ArchTest
	static final ArchRule dtosDependOnNothingAboveThem = noClasses()
			.that().resideInAPackage("..handoffs.dto..")
			.should().dependOnClassesThat().resideInAnyPackage(
					"..handoffs.controller..", "..handoffs.service..", "..handoffs.repository..");

	@ArchTest
	static final ArchRule handoffsNeverDependOnTheReportsModule = noClasses()
			.that().resideInAPackage("..handoffs..")
			.should().dependOnClassesThat().resideInAPackage("..timesheet.reports..")
			.because("a Handoff's Report check is a plain query on the reports table (ADR-015), so the "
					+ "handoffs module never needs the reports module's classes");
}
