package com.largatadev.timesheet.dashboard;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The dashboard module is split into role sub-packages (controller / dto / service /
 * repository / domain). Java has no visibility that spans sub-packages, so the move made the
 * repository and the event write model public — which on its own would let any module write
 * Events and skip the intake checks. These rules put that guarantee back, and pin the
 * module's layering: controller → service → repository, DTOs depend on nothing above them.
 */
@AnalyzeClasses(packages = "com.largatadev.timesheet", importOptions = ImportOption.DoNotIncludeTests.class)
class DashboardArchitectureTest {

	@ArchTest
	static final ArchRule repositoryAndDomainStayInsideTheModule = classes()
			.that().resideInAnyPackage("..dashboard.repository..", "..dashboard.domain..")
			.should().onlyBeAccessed().byAnyPackage("..dashboard..")
			.because("the Events log is written only through the intake's checks (ADR-014)");

	@ArchTest
	static final ArchRule controllersDoNotReachTheRepository = noClasses()
			.that().resideInAPackage("..dashboard.controller..")
			.should().dependOnClassesThat().resideInAPackage("..dashboard.repository..")
			.because("controller → service → repository (CLAUDE.md conventions)");

	@ArchTest
	static final ArchRule onlyServicesUseTheRepository = classes()
			.that().resideInAPackage("..dashboard.repository..")
			.should().onlyBeAccessed().byAnyPackage("..dashboard.service..", "..dashboard.repository..");

	@ArchTest
	static final ArchRule dtosDependOnNothingAboveThem = noClasses()
			.that().resideInAPackage("..dashboard.dto..")
			.should().dependOnClassesThat().resideInAnyPackage(
					"..dashboard.controller..", "..dashboard.service..", "..dashboard.repository..");
}
