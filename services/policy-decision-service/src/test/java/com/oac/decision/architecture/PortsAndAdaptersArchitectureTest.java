package com.oac.decision.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

@AnalyzeClasses(packages = "com.oac.decision", importOptions = ImportOption.DoNotIncludeTests.class)
class PortsAndAdaptersArchitectureTest {

    @ArchTest
    static final ArchRule application_must_not_depend_on_adapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..adapter..")
            .because("application core should be independent from adapter implementations");

    @ArchTest
    static final ArchRule application_must_be_framework_agnostic = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
            .because("application core should not depend on framework wiring concerns");

    @ArchTest
    static final ArchRule application_must_not_use_spring_stereotypes = noClasses()
            .that().resideInAPackage("..application..")
            .should().beAnnotatedWith(Component.class)
            .orShould().beAnnotatedWith(Service.class)
            .orShould().beAnnotatedWith(Repository.class)
            .orShould().beAnnotatedWith(Controller.class)
            .orShould().beAnnotatedWith(Configuration.class)
            .because("application core must stay free of framework annotations");

    @ArchTest
    static final ArchRule inbound_adapters_must_depend_on_input_ports_not_application_services = noClasses()
            .that().resideInAPackage("..adapter.in.web..")
            .and().haveSimpleNameNotEndingWith("Configuration")
            .should().dependOnClassesThat().resideInAnyPackage("..application.service..")
            .because("inbound adapters must call application input ports");

    @ArchTest
    static final ArchRule inbound_adapters_must_not_depend_on_output_ports_or_outbound_adapters = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .and().haveSimpleNameNotEndingWith("Configuration")
            .should().dependOnClassesThat().resideInAnyPackage("..application.port.out..", "..adapter.out..")
            .because("inbound adapters must not bypass use cases by reaching outbound concerns directly");

    @ArchTest
    static final ArchRule outbound_adapters_must_not_depend_on_inbound_or_application_service = noClasses()
            .that().resideInAPackage("..adapter.out..")
            .should().dependOnClassesThat().resideInAnyPackage("..adapter.in..", "..application.service..")
            .because("outbound adapters should only implement output ports and infrastructure concerns");

    @ArchTest
    static final ArchRule spring_web_controllers_must_stay_in_inbound_adapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.in.web..")
            .should().beAnnotatedWith(RestController.class)
            .orShould().beAnnotatedWith(RestControllerAdvice.class)
            .because("web controller concerns must stay inside inbound web adapters");

    @ArchTest
    static final ArchRule spring_web_request_mappings_must_stay_in_inbound_adapter = noMethods()
            .that().areDeclaredInClassesThat().resideOutsideOfPackage("..adapter.in.web..")
            .should().beAnnotatedWith(RequestMapping.class)
            .orShould().beAnnotatedWith(GetMapping.class)
            .orShould().beAnnotatedWith(PostMapping.class)
            .orShould().beAnnotatedWith(PutMapping.class)
            .orShould().beAnnotatedWith(PatchMapping.class)
            .orShould().beAnnotatedWith(DeleteMapping.class)
            .because("request mapping annotations must stay in inbound web adapters");
}
